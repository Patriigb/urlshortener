@file:Suppress("WildcardImport", "unused")

package es.unizar.urlshortener.infrastructure.delivery

import com.opencsv.CSVWriter
import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.usecases.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.util.LinkedMultiValueMap
import java.io.StringWriter
import org.springframework.messaging.simp.SimpMessagingTemplate

@WebMvcTest
@ContextConfiguration(
    classes = [
        UrlShortenerControllerImpl::class,
        RestResponseEntityExceptionHandler::class
    ]
)
class UrlShortenerControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var redirectUseCase: RedirectUseCase

    @MockBean
    private lateinit var logClickUseCase: LogClickUseCase

    @MockBean
    private lateinit var createShortUrlUseCase: CreateShortUrlUseCase

    @MockBean
    private lateinit var createQrUseCase: CreateQrUseCase

    @MockBean
    private lateinit var shortUrlRepositoryService: ShortUrlRepositoryService

    @MockBean
    private lateinit var queueController: QueueController

    @MockBean
    private lateinit var messagingTemplate: SimpMessagingTemplate

    @MockBean
    private lateinit var metricsUseCase: MetricsUseCase

    @MockBean
    private lateinit var processCsvUseCase: ProcessCsvUseCase

    private var userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" +
                            "(KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36 OPR/102.0.0.0"

    @Test
    fun `getSumary returns an empty headers sumary if url has not been accesed yet`() {
        val expectedInfoMap = LinkedMultiValueMap<String, Pair<String, String>>()
        
        given(logClickUseCase.getSumary("key")).willReturn(expectedInfoMap)
        mockMvc.perform(get("/api/link/{id}", "key"))
            .andExpect(status().isOk)

        verify(logClickUseCase, times(1)).getSumary("key")
    }

    @Test
    fun `getSumary returns headers sumary if url has been accesed`() {
        val expectedInfoMap = LinkedMultiValueMap<String, Pair<String, String>>()
        expectedInfoMap.add("key", Pair("CHROME_11", "WINDOWS_10"))

        given(redirectUseCase.redirectTo("key")).willReturn(Redirection("http://example.com/"))
        given(logClickUseCase.getSumary("key")).willReturn(expectedInfoMap)
        
        mockMvc.perform(get("/{id}", "key")
            .header("User-Agent", userAgent))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(redirectedUrl("http://example.com/"))

        mockMvc.perform(get("/api/link/{id}", "key"))
            .andExpect(status().isOk)

        verify(logClickUseCase, times(1)).getSumary("key")
        verify(queueController).producerMethod(any(), any())
    }

    @Test
    fun `createCsv returns a CSV file with shortened URIs`() {
        val csvDataIn = MockMultipartFile(
            "file", "test.csv", "text/csv", "URI\nhttp://example.com".toByteArray()
        )

        given(processCsvUseCase.checkCsvContent("URI\nhttp://example.com"))
            .willReturn(CsvContent(0, listOf("URI", "http://example.com")))
        given(createShortUrlUseCase.create("http://example.com", ShortUrlProperties(ip = "127.0.0.1")))
            .willReturn(ShortUrl(null,"f684a3c4", Redirection("http://example.com")))

        val response = mockMvc.perform(
            MockMvcRequestBuilders.multipart("/api/bulk")
                .file(csvDataIn)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv"))
            .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/f684a3c4"))
            .andReturn()

        // construct expected CSV with openCSV
        val expectedCsv = StringWriter()
        val csvWriter = CSVWriter(expectedCsv)
        csvWriter.writeNext(arrayOf("URI", "URI_Recortada", "Mensaje"), false)
        csvWriter.writeNext(arrayOf("http://example.com","http://localhost/f684a3c4", ""), false)

        assertEquals(expectedCsv.toString(), response.response.contentAsString)
    }

    @Test
    fun `createCsv returns a bad request if the CSV is not well formed`() {
        val csvDataIn = MockMultipartFile(
            "file", "test.csv", "text/csv", ",http://example.com".toByteArray()
        )

        given(processCsvUseCase.checkCsvContent(",http://example.com"))
            .willReturn(CsvContent(400))

        mockMvc.perform(
            MockMvcRequestBuilders.multipart("/api/bulk")
                .file(csvDataIn)
        )
            .andDo(print())
            .andExpect(status().isBadRequest)

        verify(createShortUrlUseCase, never()).create(any(), any())
    }

    @Test
    fun `redirectTo returns a redirect when the key exists`() {
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection("http://example.com/"))

        mockMvc.perform(get("/{id}", "key"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(redirectedUrl("http://example.com/"))
        
        verify(queueController).producerMethod(any(), any())
    }

    @Test
    fun `redirectTo returns a not found when the key does not exist`() {
        given(redirectUseCase.redirectTo("key"))
            .willAnswer { throw RedirectionNotFound("key") }

        mockMvc.perform(get("/{id}", "key"))
            .andDo(print())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.statusCode").value(404))

        verify(logClickUseCase, never()).logClick("key", ClickProperties(ip = "127.0.0.1"), userAgent)
    }

    @Test
    fun `getQr returns Ok if key exists and qrImage is available`() {

        // given(shortUrlRepositoryService.findByKey("f684a3c4")).willReturn(ShortUrl(
        //     null,"f684a3c4",Redirection("http://example.com/"), ShortUrlProperties(qr = true, 
        //     qrImage = "secuenciaDeBytes".toByteArray())
        // ))

        given(createQrUseCase.getQrCode("f684a3c4")).willReturn(ShortUrl(
            null,"f684a3c4",Redirection("http://example.com/"), ShortUrlProperties(qr = true, 
            qrImage = "secuenciaDeBytes".toByteArray())
        ))
       
        mockMvc.perform(get("/{id}/qr", "f684a3c4"))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
            .andExpect(content().bytes("secuenciaDeBytes".toByteArray()))

        verify(createQrUseCase).getQrCode("f684a3c4")
    
    }

    @Test
    fun `getQr returns Retry_after if key exists and qrImage is not available`() {


        given(createQrUseCase.getQrCode("f684a3c4")).willThrow(QrNotReady("f684a3c4"))

        val errorResponse = "Imagen QR no disponible. Inténtalo más tarde."
       
        mockMvc.perform(get("/{id}/qr", "f684a3c4"))
            .andDo(print())
            .andExpect(status().isBadRequest)
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
            .andExpect(jsonPath("$.error").value(errorResponse))

        verify(createQrUseCase).getQrCode("f684a3c4")
    
    }

    @Test
    fun `getQr returns NOT_FOUND if key does not exist`() {

        given(createQrUseCase.getQrCode("key")).willThrow(RedirectionNotFound("key"))

        mockMvc.perform(get("/{id}/qr", "key"))
            .andDo(print())
            .andExpect(status().isNotFound)

    }

    @Test
    fun `creates includes a qr field in the returned json`() {
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                data = ShortUrlProperties(ip = "127.0.0.1", qr = true)
            )
        ).willReturn(ShortUrl(null,"f684a3c4", Redirection("http://example.com/")))

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://example.com/")
                .param("generateQr", true.toString())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.url").value("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.qr").value("http://localhost/f684a3c4/qr"))
    }

    @Test
    fun `creates returns a basic redirect if it can compute a hash`() {
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willReturn(ShortUrl(null,"f684a3c4", Redirection("http://example.com/")))

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://example.com/")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.url").value("http://localhost/f684a3c4"))
    }

    @Test
    fun `creates returns bad request if it can compute a hash`() {
        given(
            createShortUrlUseCase.create(
                url = "ftp://example.com/",
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willAnswer { throw InvalidUrlException("ftp://example.com/") }

        mockMvc.perform(
            post("/api/link")
                .param("url", "ftp://example.com/")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.statusCode").value(400))
    }
}
