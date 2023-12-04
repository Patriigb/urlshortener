@file:Suppress("LongParameterList", "TooGenericExceptionCaught", "TooManyFunctions", "ReturnCount")

package es.unizar.urlshortener.infrastructure.delivery

import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.ShortUrlProperties
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import es.unizar.urlshortener.core.usecases.CreateQrUseCase
import es.unizar.urlshortener.core.usecases.ProcessCsvUseCase
import es.unizar.urlshortener.core.usecases.LogClickUseCase
import es.unizar.urlshortener.core.usecases.RedirectUseCase
import es.unizar.urlshortener.core.usecases.MetricsUseCase
import es.unizar.urlshortener.core.QueueController
import jakarta.servlet.http.HttpServletRequest
import org.springframework.hateoas.server.mvc.linkTo
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import org.springframework.web.multipart.MultipartFile
import es.unizar.urlshortener.core.ShortUrlRepositoryService
import java.io.StringReader
import java.io.StringWriter
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.annotation.SubscribeMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.config.MessageBrokerRegistry
//import jakarta.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

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
    fun getMetrics(request: HttpServletRequest): ResponseEntity<Any>

    /*
    * Gets a specific metric
    * */
    fun getMetric(id: String, request: HttpServletRequest): ResponseEntity<Any>
    
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


data class ServerMessage(
    val type: String? = null,
    val body: String? = null
)

data class WsData(
    val urls: List<String>,
    val generateQr: Boolean
)

/**
 * Data returned after the creation of a short url from a csv file.
 */
data class ShortInfo(
    val originalUri: String,
    val shortenedUri: URI,
    val errorMessage: String
)

class RemoteAddressHandshakeInterceptor : HandshakeInterceptor {

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        // Obtener la dirección remota del cliente desde la solicitud y almacenarla como un atributo de la sesión
        attributes["remoteAddr"] = request.remoteAddress.address.hostAddress
        return true
    }
    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) {
        println("WebSocket handshake completed successfully.")
    }
}

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config.enableSimpleBroker("/topic")
        config.setApplicationDestinationPrefixes("/topic")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/api/fast-bulk")
            .withSockJS()
            .setInterceptors(RemoteAddressHandshakeInterceptor())
    }
}

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
    val shortUrlRepository: ShortUrlRepositoryService,
    val processCsvUseCase: ProcessCsvUseCase,
    val controlador: QueueController,
    val metricsUseCase: MetricsUseCase

) : UrlShortenerController {

    @Autowired
    private lateinit var messagingTemplate: SimpMessagingTemplate

    @GetMapping("/api/link/{id}")
    override fun getSumary(@PathVariable("id") id: String): ResponseEntity<Sumary> {
            println("el id es: $id")
            
            val datos = logClickUseCase.getSumary(id)

            val response = Sumary(info = datos)
            return ResponseEntity<Sumary>(response, HttpStatus.OK)
    }


    @GetMapping("/{id:(?!api|index).*}")
    override fun redirectTo(@PathVariable id: String, request: HttpServletRequest?): ResponseEntity<Unit> {
            val redirection = redirectUseCase.redirectTo(id)
        
            val logFunction: suspend () -> Unit = {
                logClickUseCase.logClick(
                    id, ClickProperties(ip = request?.remoteAddr), request?.getHeader("User-Agent")
                )
            }
            controlador.producerMethod("logClick", logFunction)

            val headers = HttpHeaders()
            headers.location = URI.create(redirection.target)

            return ResponseEntity<Unit>(headers, HttpStatus.valueOf(redirection.mode))
    }

    @GetMapping("/{id}/qr")
    override fun getQr(@PathVariable("id") id: String, request: HttpServletRequest): ResponseEntity<Any> {
        // Verificar si el id existe en la base de datos
        val shortUrl = shortUrlRepository.findByKey(id)
        if (shortUrl != null && shortUrl.properties.qr == true) {

            if(shortUrl.properties.qrImage == null){

                val errorResponse = mapOf("error" to "Imagen QR no disponible. Intentalo más tarde.")
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .header("Retry-After", "5")
                    .body(errorResponse)
            }
            
            // Devolver imagen con tipo de contenido correcto
            return ResponseEntity.ok().header("Content-Type", "image/png").body(shortUrl.properties.qrImage)
        } else {
            // Devolver 404 si el id no existe
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    // @Autowired
    // private val metricsUseCase: MetricsUseCase = MetricsUseCaseImpl(metricsController.registry)

    @Scheduled(fixedRate = 10000) // Ejemplo: Cada diez segundos
    fun scheduleMetricsRegistration() {
        
        val miFunction: suspend () -> Unit = {
            metricsUseCase.registerOperatingSystemMetrics()
        }
        controlador.producerMethod("registerOperatingSystemMetrics", miFunction)

    }

    @Scheduled(fixedRate = 10000) // Ejemplo: Cada diez segundos
    fun scheduleMetricsRegistration2() {

        val miFunction: suspend () -> Unit = {
            metricsUseCase.registerShortUrlsCount()
        }
        controlador.producerMethod("registerShortUrlsCount", miFunction)

    }

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

        println("URIIIIIII    $uriMetric")
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
                // comprobar que headersSumary no es null
                val miFuncion: suspend () -> Unit = {
                    createQrUseCase.generateQRCode(urlQr, it)
                }
                controlador.producerMethod("generateQRCode", miFuncion)
                
              //  controlador.consumerMethod()
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

            val (originalUri, shortenedUri, errorMessage) = shortUrl(uri, data, request, false, null)
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
                        val miFuncion: suspend () -> Unit = { createQrUseCase.generateQRCode(urlQr, shortUrl) }
                        controlador.producerMethod("generateQRCode", miFuncion)
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

    @MessageMapping("/csv")
    @SendTo("/topic/csv")
    fun fastBulk(data: WsData, accessor: SimpMessageHeaderAccessor) {
        val uris = data.urls
        val checkQr = data.generateQr
        val remoteAddr = accessor.sessionAttributes?.get("remoteAddr").toString()

        for (uri in uris) {
            var msg: String
            val (originalUri, shortenedUri, errorMessage) = shortUrl(uri, null, null, true, remoteAddr)

            if (errorMessage.isBlank()) {
                val shortUrl = createShortUrlUseCase.create(
                    url = originalUri,
                    data = ShortUrlProperties(
                        ip = remoteAddr,
                        qr = checkQr
                    )
                )

                msg = if (checkQr) {
                    val urlQr = "$shortenedUri/qr"
                    val miFuncion: suspend () -> Unit = { createQrUseCase.generateQRCode(urlQr, shortUrl) }
                    controlador.producerMethod("generateQRCode", miFuncion)
                    "$originalUri >>> $shortenedUri >>> $urlQr"
                } else {
                    "$originalUri >>> $shortenedUri"
                }

                messagingTemplate.convertAndSend(
                    "/topic/csv",
                    ServerMessage("server",msg))

            } else {
                messagingTemplate.convertAndSend(
                    "/topic/csv",
                    ServerMessage("server","Ha ocurrido un error: $errorMessage"))
            }
        }
    }

    @SubscribeMapping("/csv")
    fun subscribeToCsv() {
        val message = ServerMessage("server","¡Hola! Escribe las urls separadas por espacios.")
        messagingTemplate.convertAndSend("/topic/csv", message)
    }

    /**
     * Creates a short url from a [uri].
     * @return a [ShortInfo] with the original uri, the shortened uri and an error message if any.
     */
    private fun shortUrl( uri: String, data: CsvDataIn?, request: HttpServletRequest?,
                          isWs: Boolean, remoteAddr: String?): ShortInfo{
        try {
            val shortUrlDataIn = ShortUrlDataIn(uri, data?.sponsor, false)
            val ip = if (isWs) remoteAddr else request?.remoteAddr

            val response = createShortUrlUseCase.create(
                url = shortUrlDataIn.url,
                data = ShortUrlProperties(
                    ip = ip,
                    sponsor = shortUrlDataIn.sponsor
                )
            )

            val shortenedUri = URI("http://localhost:8080").resolve(
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
