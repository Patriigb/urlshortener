@file:Suppress("WildcardImport", "unused")

package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.usecases.CreateQrUseCase
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import es.unizar.urlshortener.core.usecases.InfoHeadersUseCase
import es.unizar.urlshortener.core.usecases.ProcessCsvUseCase
import es.unizar.urlshortener.core.usecases.LogClickUseCase
import es.unizar.urlshortener.core.usecases.RedirectUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

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
    private lateinit var infoHeadersUseCase: InfoHeadersUseCase

    @MockBean
    private lateinit var shortUrlRepositoryService: ShortUrlRepositoryService

    @MockBean
    private lateinit var processCsvUseCase: ProcessCsvUseCase

    private var userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" +
                            "(KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36 OPR/102.0.0.0"

    @Test
    fun `getSumary returns an empty headers sumary if url has not been accesed yet`() {
        val expectedInfoMap = LinkedMultiValueMap<String, Pair<String, String>>()
        
        given(infoHeadersUseCase.getSumary("key")).willReturn(expectedInfoMap)
        mockMvc.perform(get("/api/link/{id}", "key"))
            .andExpect(status().isOk)

        verify(infoHeadersUseCase, times(1)).getSumary("key")
    }

    @Test
    fun `getSumary returns headers sumary if url has been accesed`() {
        val expectedInfoMap = LinkedMultiValueMap<String, Pair<String, String>>()
        expectedInfoMap.add("key", Pair("CHROME_11", "WINDOWS_10"))

        given(redirectUseCase.redirectTo("key")).willReturn(Redirection("http://example.com/"))
        given(infoHeadersUseCase.getSumary("key")).willReturn(expectedInfoMap)
        
        mockMvc.perform(get("/{id}", "key")
            .header("User-Agent", userAgent))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(redirectedUrl("http://example.com/"))

        mockMvc.perform(get("/api/link/{id}", "key"))
            .andExpect(status().isOk)

        verify(infoHeadersUseCase, times(1)).getSumary("key")
        verify(logClickUseCase).logClick("key", ClickProperties(ip = "127.0.0.1"))
        verify(infoHeadersUseCase).logHeader("key", userAgent)
    }

    @Test
    fun `redirectTo returns a redirect when the key exists`() {
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection("http://example.com/"))

        mockMvc.perform(get("/{id}", "key"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(redirectedUrl("http://example.com/"))

        verify(logClickUseCase).logClick("key", ClickProperties(ip = "127.0.0.1"))
    }

    @Test
    fun `redirectTo returns a not found when the key does not exist`() {
        given(redirectUseCase.redirectTo("key"))
            .willAnswer { throw RedirectionNotFound("key") }

        mockMvc.perform(get("/{id}", "key"))
            .andDo(print())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.statusCode").value(404))

        verify(logClickUseCase, never()).logClick("key", ClickProperties(ip = "127.0.0.1"))
    }

    @Test
    fun `getQr returns Ok if key exists`() {

        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://example.com/")))

        //first it needs to create the short url
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

        verify(shortUrlRepositoryService).save(ShortUrl("f684a3c4", Redirection("http://example.com/")))
        mockMvc.perform(get("/{id}/qr", "f684a3c4"))
            .andDo(print())
            .andExpect(status().isOk)

        verify(createQrUseCase).generateQRCode("http://localhost/f684a3c4")
    }

    @Test
    fun `getQr returns NOT_FOUND if key does not exist`() {

        mockMvc.perform(get("/{id}/qr", "key"))
            .andDo(print())
            .andExpect(status().isNotFound)

        verify(createQrUseCase, never()).generateQRCode("http://localhost/key")
    }

    @Test
    fun `creates includes a qr field in the returned json`() {
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://example.com/")))

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
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://example.com/")))

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
