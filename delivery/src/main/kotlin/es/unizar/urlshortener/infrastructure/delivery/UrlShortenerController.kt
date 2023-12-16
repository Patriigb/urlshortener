@file:Suppress("LongParameterList", "TooGenericExceptionCaught", "TooManyFunctions", "ReturnCount")

package es.unizar.urlshortener.infrastructure.delivery

import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.RedirectionNotFound
import es.unizar.urlshortener.core.QrNotFound
import es.unizar.urlshortener.core.QrNotReady
import es.unizar.urlshortener.core.QueueController
import es.unizar.urlshortener.core.ShortUrlProperties
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import es.unizar.urlshortener.core.usecases.CreateQrUseCase
import es.unizar.urlshortener.core.usecases.ProcessCsvUseCase
import es.unizar.urlshortener.core.usecases.LogClickUseCase
import es.unizar.urlshortener.core.usecases.RedirectUseCase
import es.unizar.urlshortener.core.usecases.MetricsUseCase
import io.micrometer.core.instrument.Gauge
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.hateoas.server.mvc.linkTo
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.annotation.SubscribeMapping
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import io.micrometer.core.instrument.MeterRegistry
import java.io.StringReader
import java.io.StringWriter
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse


const val OK = 200
const val BAD_REQUEST = 400

/**
 * The specification of the controller.
 */
interface UrlShortenerController {

    /**
     * Redirects and logs a short url identified by its [id].
     *
     * **Note**: Delivery of use cases [RedirectUseCase] and [LogClickUseCase].
     */
    fun redirectTo(id: String, request: HttpServletRequest?): ResponseEntity<Unit>

    /**
     * Creates a short url from details provided in [data].
     *
     * **Note**: Delivery of use case [CreateShortUrlUseCase].
     */
    fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut>

    /**
     * Gets the summary of a short url identified by its [id].
     */
    fun getSumary(id: String): ResponseEntity<Sumary>

    /**
     * Creates a CSV file with the short urls from a CSV file with the original urls.
     */
    fun createCsv(data: CsvDataIn, request: HttpServletRequest): ResponseEntity<String>

    /**
     * Gets the QR code of a short url identified by its [id].
     */
    fun getQr(id: String, request: HttpServletRequest): ResponseEntity<Any>
    
    /**
     * Gets the metrics of the system.
     */
    // fun getMetrics(request: HttpServletRequest): ResponseEntity<Any>

    /*
    * Gets a specific metric
    * */
    // fun getMetric(id: String, request: HttpServletRequest): ResponseEntity<Any>
    
}

/**
 * Data required to get the summary of a short url.
 */
data class Sumary(
    val info: MultiValueMap<String, Pair<String, String>> =  LinkedMultiValueMap()
)

/**
 * Data required to create a short url.
 */
data class ShortUrlDataIn(
    val url: String,
    val sponsor: String? = null,
    val generateQr: Boolean?
)

/**
 * Data returned after the creation of a short url.
 */
data class ShortUrlDataOut(
    val url: URI? = null,
    val properties: Map<String, Any?> = emptyMap(),
    val qr: String? = null
)

/**
 * Data required to create a short url from a csv file.
 */
data class CsvDataIn(
    val file: MultipartFile,
    val sponsor: String? = null
)

/**
 * Data returned after the creation of a short url from a csv file.
 */
data class ShortInfo(
    val originalUri: String,
    val shortenedUri: URI,
    val errorMessage: String
)

/**
 * The implementation of the controller.
 *
 * **Note**: Spring Boot is able to discover this [RestController] without further configuration.
 */
@RestController
@EnableScheduling
@Component
class UrlShortenerControllerImpl(
    val redirectUseCase: RedirectUseCase,
    val logClickUseCase: LogClickUseCase,
    val createShortUrlUseCase: CreateShortUrlUseCase,
    val createQrUseCase: CreateQrUseCase,
    val processCsvUseCase: ProcessCsvUseCase,
    val queueController: QueueController,
    val metricsUseCase: MetricsUseCase

) : UrlShortenerController {

    private val log = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/api/link/{id}")
    override fun getSumary(@PathVariable("id") id: String): ResponseEntity<Sumary> {
            //println("el id es: $id")
            try{
                val datos = logClickUseCase.getSumary(id)

                val response = Sumary(info = datos)
                return ResponseEntity<Sumary>(response, HttpStatus.OK)
            } catch (e: RedirectionNotFound) {
                log.error(e.message)
                return ResponseEntity.notFound().build();
            }
    }


    @GetMapping("/{id:(?!api|index).*}")
    override fun redirectTo(@PathVariable id: String, request: HttpServletRequest?): ResponseEntity<Unit> {
            val redirection = redirectUseCase.redirectTo(id)

            queueController.producerMethod("logClick") {
                logClickUseCase.logClick(
                    id, ClickProperties(ip = request?.remoteAddr), request?.getHeader("User-Agent")
                )
            }

            val headers = HttpHeaders()
            headers.location = URI.create(redirection.target)

            return ResponseEntity<Unit>(headers, HttpStatus.valueOf(redirection.mode))
    }

    @GetMapping("/{id}/qr")
    override fun getQr(@PathVariable("id") id: String, request: HttpServletRequest): ResponseEntity<Any> {

        try{
            val shortUrl = createQrUseCase.getQrCode(id)
        
            // Devolver imagen con tipo de contenido correcto
            return ResponseEntity.ok().header("Content-Type", "image/png").body(shortUrl.properties.qrImage)
        } catch (e: RedirectionNotFound) {
            log.error(e.message)
            val errorResponse = mapOf("error" to "No se encontró la redirección con el ID especificado.")
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
            
        } catch (e: QrNotFound) {
            log.error(e.message)
            val errorResponse = mapOf("error" to "El código QR no está habilitado para esta redirección.")
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
            
        } catch (e: QrNotReady) {
            log.error(e.message)
            val errorResponse = mapOf("error" to "URI de destino no validada todavía")
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Retry-After", "5")
                .body(errorResponse)  
        }
    }




    @Scheduled(fixedRate = 10000) // Ejemplo: Cada diez segundos
    fun scheduleMetricsRegistration() {

        queueController.producerMethod("registerOperatingSystemMetrics") {
            metricsUseCase.registerOperatingSystemMetrics()
        }

    }
    @Scheduled(fixedRate = 10000) // Ejemplo: Cada diez segundos
    fun scheduleMetricsRegistration2() {

        queueController.producerMethod("registerShortUrlsCount") {
            metricsUseCase.registerShortUrlsCount()
        }

    }

    /*
    @GetMapping("/api/stats/metrics")
    override fun getMetrics(request: HttpServletRequest): ResponseEntity<Any> {

        // Obtén la URI actual de la solicitud
        val currentUri = URI.create(request.requestURL.toString())
        val uriMetrics = URI("${currentUri.scheme}://${currentUri.host}:${currentUri.port}/actuator/metrics")

        val client = HttpClient.newBuilder().build()
        val httpRequest = HttpRequest.newBuilder()
            .uri(uriMetrics)
            .build()

        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        if(response.statusCode() == OK){
            return ResponseEntity.ok().header("Content-Type", "application/json")
                .body(response.body())
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

    }


    @GetMapping("/api/stats/metrics/{id}")
    override fun getMetric(@PathVariable("id") id: String, request: HttpServletRequest): ResponseEntity<Any> {
        // Obtén la URI actual de la solicitud
        val currentUri = URI.create(request.requestURL.toString())
        val uriMetric = URI("${currentUri.scheme}://${currentUri.host}:${currentUri.port}/actuator/metrics/${id}")

        val client = HttpClient.newBuilder().build()
        val httpRequest = HttpRequest.newBuilder()
            .uri(uriMetric)
            .build()

        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        if(response.statusCode() == OK){
            return ResponseEntity.ok().header("Content-Type", "application/json")
                .body(response.body())
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }
     */

    @PostMapping("/api/link", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    override fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut> =
        createShortUrlUseCase.create(
            url = data.url,
            data = ShortUrlProperties(
                ip = request.remoteAddr,
                sponsor = data.sponsor, 
                qr = data.generateQr
            )
        ).let {
            val h = HttpHeaders()
            val url = linkTo<UrlShortenerControllerImpl> { redirectTo(it.hash, request) }.toUri()
            h.location = url

            // url del qr más /qr
            val response = if (data.generateQr == true) {
                val urlQr = "$url/qr"

                queueController.producerMethod("generateQRCode") {
                    createQrUseCase.generateQRCode(urlQr, it)
                }

                ShortUrlDataOut(
                    url = url,
                    properties = mapOf(
                        "safe" to it.properties.safe,
                        "qr" to data.generateQr
                    ),
                    qr = urlQr
                )
            } else {
                ShortUrlDataOut(
                    url = url,
                    properties = mapOf(
                        "safe" to it.properties.safe,
                        "qr" to data.generateQr
                    )
                )
            }
            ResponseEntity<ShortUrlDataOut>(response, h, HttpStatus.CREATED)
        }

    @PostMapping("/api/bulk", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    override fun createCsv(data: CsvDataIn, request: HttpServletRequest): ResponseEntity<String> {
        val csvContent = data.file.bytes.toString(Charsets.UTF_8)
        val result = processCsvUseCase.checkCsvContent(csvContent)

        if (result.result == BAD_REQUEST || result.result == OK) {
            return ResponseEntity(HttpStatus.valueOf(result.result))
        }

        val checkQr = result.result == 1
        var urlQr = ""
        val lines = CSVReader(StringReader(csvContent)).readAll().map { it.map(String::trim) }

        val h = HttpHeaders().apply { contentType = MediaType.parseMediaType("text/csv") }
        val resultCsv = StringWriter()
        val csvWriter = CSVWriter(resultCsv)
        var firstUri = true

        for (i in 1 until lines.size) {
            val line = lines[i]
            val uri = line[0]
            val qr = if (checkQr && line.size > 1 && line[1].isNotBlank()) line[1] else null

            val (originalUri, shortenedUri, errorMessage) = shortUrl(uri, data, request, false, null, null)
            if (firstUri) {
                h.location = shortenedUri
                if (checkQr) {
                    csvWriter.writeNext(arrayOf("URI", "URI_Recortada", "URI_QR", "Mensaje"), false)
                } else {
                    csvWriter.writeNext(arrayOf("URI", "URI_Recortada", "Mensaje"), false)
                }
                firstUri = false
            }

            if (errorMessage.isBlank()) {
                val shortUrl = createShortUrlUseCase.create(
                    url = originalUri,
                    data = ShortUrlProperties(
                        ip = request.remoteAddr,
                        qr = qr != null
                    )
                )
                if (checkQr && qr != null) {
                        urlQr = "$shortenedUri/qr"
                        queueController.producerMethod("generateQRCode") {
                            createQrUseCase.generateQRCode(urlQr, shortUrl)
                        }
                }
            }
            if (checkQr) {
                csvWriter.writeNext(arrayOf(originalUri, "$shortenedUri", urlQr, errorMessage), false)
            } else {
                csvWriter.writeNext(arrayOf(originalUri, "$shortenedUri", errorMessage), false)
            }
        }
        csvWriter.close()
        return ResponseEntity(resultCsv.toString(), h, HttpStatus.CREATED)
    }

    /**
     * Creates a short url from a [uri].
     * @return a [ShortInfo] with the original uri, the shortened uri and an error message if any.
     */
    fun shortUrl(uri: String, data: CsvDataIn?, request: HttpServletRequest?,
                 isWs: Boolean, localAddr: InetAddress?, port: String?): ShortInfo {
        try {
            val shortUrlDataIn = ShortUrlDataIn(uri, data?.sponsor, false)
            val ip = if (isWs) localAddr.toString().removePrefix("/") else request?.remoteAddr
            log.info(ip)

            val response = createShortUrlUseCase.create(
                url = shortUrlDataIn.url,
                data = ShortUrlProperties(
                    ip = ip,
                    sponsor = shortUrlDataIn.sponsor
                )
            )

            val ipUri = if (localAddr is Inet6Address) "[$ip]:$port" else ip
            val shortenedUri = URI("http://$ipUri").resolve(
                linkTo<UrlShortenerControllerImpl> { redirectTo(response.hash, request) }.toUri())
            val errorMessage = if (response.properties.safe) "" else "ERROR"

            return ShortInfo(uri, shortenedUri, errorMessage)
        } catch (e: Exception) {
            val shortenedUri = URI("")
            val errorMessage = e.message ?: "ERROR"
            return ShortInfo(uri, shortenedUri, errorMessage)
        }
    }
}
