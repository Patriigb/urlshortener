@file:Suppress("LongParameterList", "TooManyFunctions", "ReturnCount")

package es.unizar.urlshortener.infrastructure.delivery

import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.QrNotFound
import es.unizar.urlshortener.core.QrNotReady
import es.unizar.urlshortener.core.ShortUrlProperties
import es.unizar.urlshortener.core.RedirectionNotFound
import es.unizar.urlshortener.core.InvalidUrlException
import es.unizar.urlshortener.core.QueueController
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import es.unizar.urlshortener.core.usecases.CreateQrUseCase
import es.unizar.urlshortener.core.usecases.ProcessCsvUseCase
import es.unizar.urlshortener.core.usecases.LogClickUseCase
import es.unizar.urlshortener.core.usecases.RedirectUseCase
import es.unizar.urlshortener.core.usecases.MetricsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.hateoas.server.mvc.linkTo
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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
import java.io.StringReader
import java.io.StringWriter
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

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
     * 
     * **Note**: Delivery of use case [LogClickUseCase].
     */
    fun getSumary(id: String): ResponseEntity<Sumary>

    /**
     * Creates a CSV file with the short urls from a CSV file with the original urls.
     * 
     * **Note**: Delivery of use case [ProcessCsvUseCase].
     */
    fun createCsv(data: CsvDataIn, request: HttpServletRequest): ResponseEntity<String>

    /**
     * Gets the QR code of a short url identified by its [id].
     * 
     * **Note**: Delivery of use case [CreateQrUseCase].
     */
    fun getQr(id: String, request: HttpServletRequest): ResponseEntity<Any>
    
}

/**
 * Data required to get the summary of a short url.
 */
data class Sumary(
    @Schema(description = "Información relacionada con la URL")
    val info: MultiValueMap<String, Pair<String, String>> =  LinkedMultiValueMap()
)

/**
 * Data required to create a short url.
 */
data class ShortUrlDataIn(
    @Schema(description = "URL original que se acortará", required = true)
    val url: String,
    @Schema(description = "Información del sponsor")
    val sponsor: String? = null,
    @Schema(description = "Para generar o no un código QR")
    val generateQr: Boolean?
)

/**
 * Data returned after the creation of a short url.
 */
data class ShortUrlDataOut(
    @Schema(description = "URL acortada")
    val url: URI? = null,
    @Schema(description = "Propiedades adicionales relacionadas con la URL corta")
    val properties: Map<String, Any?> = emptyMap(),
    @Schema(description = "Código QR asociado con la URL corta")
    val qr: String? = null
)

/**
 * Data required to create a short url from a csv file.
 */
data class CsvDataIn(
    @Schema(description = "Archivo CSV que contiene las URL que se acortarán", required = true)
    val file: MultipartFile,
    @Schema(description = "Información del sponsor")
    val sponsor: String? = null
)

/**
 * Data returned after the creation of a short url from a csv file.
 */
data class ShortInfo(
    @Schema(description = "URL original")
    val originalUri: String,
    @Schema(description = "URL acortada")
    val shortenedUri: URI,
    @Schema(description = "Mensaje de error, si lo hay")
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

    @Operation(
        summary = "Obtiene la información para una URI específica",
        description = "Obtiene el resumen de información correspondiente al ID de la URI proporcionada.",
        responses = [
            ApiResponse(responseCode = "200", description = "Éxito en la obtención del resumen",
                        content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "404", description = "URI no encontrada"),
            ApiResponse(responseCode = "400", description = "Todavía no se puede redireccionar")
        ]
    )
    @GetMapping("/api/link/{id}")
    override fun getSumary(@PathVariable("id") id: String): ResponseEntity<Sumary> {
            try{
                val datos = logClickUseCase.getSumary(id)
                val response = Sumary(info = datos)
                return ResponseEntity<Sumary>(response, HttpStatus.OK)
            } catch (e: RedirectionNotFound) {
                log.error(e.message)
                return ResponseEntity.notFound().build();
            }
    }

    @Operation(
        summary = "Redirige a la URL especificada",
        description = "Redirige a la URL correspondiente al ID proporcionado.",
        responses = [
            ApiResponse(responseCode = "302", description = "Redirección exitosa",
                content = [Content()]),
            ApiResponse(responseCode = "404", description = "ID no encontrado",
                content = [Content()])
        ]
    )
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

    @Operation(
        summary = "Obtiene el código QR para una URI específica",
        description = "Obtiene el código QR correspondiente al ID proporcionado.",
        responses = [
            ApiResponse(responseCode = "200", description = "Éxito en la obtención del código QR",
                        content = [Content(mediaType = "image/png")]),
            ApiResponse(responseCode = "404", description = "ID no encontrado o código QR no habilitado",
                        content = [Content()]),
            ApiResponse(responseCode = "400", description = "URI de destino no validada todavía",
                        content = [Content()])
        ]
    )
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

    /**
     * Registers the operating system metrics.
     * 
     * **Note**: Delivery of use case [MetricsUseCase].
     */
    @Scheduled(fixedRate = 10000)
    fun scheduleMetricsRegistration() {

        queueController.producerMethod("registerOperatingSystemMetrics") {
            metricsUseCase.registerOperatingSystemMetrics()
        }
    }

    /**
     * Registers the short urls count metrics.
     * 
     * **Note**: Delivery of use case [MetricsUseCase].
     */
    @Scheduled(fixedRate = 10000)
    fun scheduleMetricsRegistration2() {

        queueController.producerMethod("registerShortUrlsCount") {
            metricsUseCase.registerShortUrlsCount()
        }
    }

    @Operation(
        summary = "Acorta una URL y genera un código QR opcionalmente",
        description = "Crea una URL corta para la URL proporcionada, con la opción de generar un código QR.",
        responses = [
            ApiResponse(responseCode = "201", description = "URL corta creada exitosamente",
                        content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "400", description = "Error en la solicitud o datos inválidos",
                        content = [Content(mediaType = "application/json")])
        ]
    )
    @PostMapping("/api/link", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    override fun shortener(
        @RequestBody(
            description = "Datos para crear una URL corta", 
            required = true,
            content = [Content(
                mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                schema = Schema(implementation = ShortUrlDataIn::class)
            )]
        )
        data: ShortUrlDataIn, 
        request: HttpServletRequest
    ): ResponseEntity<ShortUrlDataOut> =
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

    @Operation(
        summary = "Procesa un archivo CSV para crear múltiples URL cortas",
        description = "Procesa un archivo CSV que contiene URIs para crear múltiples URL cortas, y opcionalmente QRs.",
        responses = [
            ApiResponse(responseCode = "201", description = "URLs cortas creadas exitosamente",
                        content = [Content(mediaType = "text/csv")]),
            ApiResponse(responseCode = "400", description = "Error en la solicitud o datos inválidos",
                        content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "200", description = "El fichero está vacío")
        ]
    )
    @PostMapping("/api/bulk", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    override fun createCsv(
        @RequestBody(
            description = "Datos para crear múltiples URL cortas", 
            required = true,
            content = [Content(
                mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                schema = Schema(implementation = CsvDataIn::class)
            )]
        )
        data: CsvDataIn, 
        request: HttpServletRequest
    ): ResponseEntity<String> {
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
     * Creates a short url from a [uri] for CSV funcionality.
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
        } catch (e: InvalidUrlException) {
            return ShortInfo(uri, URI(""), e.message ?: "Invalid Url")
        } catch (e: RedirectionNotFound) {
            return ShortInfo(uri, URI(""), e.message ?: "Redirection Not Found")
        }
    }
}
