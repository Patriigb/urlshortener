package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.ShortUrlProperties
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import es.unizar.urlshortener.core.usecases.CreateQrUseCase
import es.unizar.urlshortener.core.usecases.InfoHeadersUseCase
import es.unizar.urlshortener.core.usecases.LogClickUseCase
import es.unizar.urlshortener.core.usecases.RedirectUseCase
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.multipart.MultipartFile
import es.unizar.urlshortener.core.ShortUrlRepositoryService


/**
 * The specification of the controller.
 */
interface UrlShortenerController {

    /**
     * Redirects and logs a short url identified by its [id].
     *
     * **Note**: Delivery of use cases [RedirectUseCase] and [LogClickUseCase].
     */
    fun redirectTo(id: String, request: HttpServletRequest): ResponseEntity<Unit>

    /**
     * Creates a short url from details provided in [data].
     *
     * **Note**: Delivery of use case [CreateShortUrlUseCase].
     */
    fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut>

    fun getSumary(id: String): ResponseEntity<Sumary>

    /**
     * Creates a CSV file with the short urls from a CSV file with the original urls.
     */
    fun createCsv(data: CsvDataIn, request: HttpServletRequest): ResponseEntity<String>

    fun getQr(id: String, request: HttpServletRequest): ResponseEntity<ByteArray>
}

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
    val properties: Map<String, Any> = emptyMap(),
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
class UrlShortenerControllerImpl(
    val redirectUseCase: RedirectUseCase,
    val logClickUseCase: LogClickUseCase,
    val createShortUrlUseCase: CreateShortUrlUseCase,
    val createQrUseCase: CreateQrUseCase,
    val infoHeadersUseCase: InfoHeadersUseCase,
    val shortUrlRepository: ShortUrlRepositoryService
) : UrlShortenerController {

    @GetMapping("/api/link/{id}")
    override fun getSumary(@PathVariable("id") id: String): ResponseEntity<Sumary> =
        infoHeadersUseCase.getSumary(id).let{
            val response = Sumary(info = it)
            ResponseEntity<Sumary>(response, HttpStatus.CREATED)
        }

    @GetMapping("/{id:(?!api|index).*}")
    override fun redirectTo(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<Unit> =
        redirectUseCase.redirectTo(id).let {
            if (request.getHeader("User-Agent") != null) {
                infoHeadersUseCase.logHeader(id, request.getHeader("User-Agent"))
            }
            logClickUseCase.logClick(id, ClickProperties(ip = request.remoteAddr))
            val h = HttpHeaders()
            h.location = URI.create(it.target)
            ResponseEntity<Unit>(h, HttpStatus.valueOf(it.mode))
        }

    @GetMapping("/{id}/qr")
    override fun getQr(@PathVariable("id") id: String, request: HttpServletRequest): ResponseEntity<ByteArray> {
        // Verificar si el id existe en la base de datos

        val shortUrl = shortUrlRepository.findByKey(id)
        if (shortUrl != null) {
            
            // Obtener la URL completa
            val requestURL = request.requestURL.toString().substringBeforeLast("/qr")
            val qrImage = createQrUseCase.generateQRCode(requestURL)
            
            // Devolver imagen con tipo de contenido correcto
            return ResponseEntity.ok().header("Content-Type", "image/png").body(qrImage)
        } else {
            // Devolver 404 si el id no existe
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    // @PostMapping("/{id}/validate")
    // fun validate(@PathVariable id: String): ResponseEntity<Any> {
    //     // Lógica de validación
    //     val isValid = validarId(id)

    //     if (isValid) {
    //         return ResponseEntity.ok().build()
    //     } else {
    //         // Devolver 400 y Retry-After
    //         return ResponseEntity.status(HttpStatus.BAD_REQUEST)
    //             .header("Retry-After", "60")  // Tiempo en segundos
    //             .build()
    //     }
    // }

    // private fun validarId(id: String): Boolean {
    //     // Lógica de validación del id (simulado)
    //     return id.length > 5
    // }

    // private fun obtenerImagenQr(id: String): ByteArray {
    //     // Lógica para obtener la imagen QR (simulado)
    //     return "ImagenQR_$id".toByteArray()
    // }

    @PostMapping("/api/link", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    override fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut> =
        createShortUrlUseCase.create(
            url = data.url,
            data = ShortUrlProperties(
                ip = request.remoteAddr,
                sponsor = data.sponsor
            )
        ).let {
            val h = HttpHeaders()
            val url = linkTo<UrlShortenerControllerImpl> { redirectTo(it.hash, request) }.toUri()
            val headersSumary = getSumary(it.hash)
            h.location = url
            // url del qr más /qr
            val response = if (data.generateQr == true && headersSumary.body != null) {
                val urlQr = url.toString() + "/qr"
                ShortUrlDataOut(
                    url = url,
                    properties = mapOf(
                        "safe" to it.properties.safe,
                        "sumary" to headersSumary.body.info
                    ),
                    qr = urlQr
                )
            } else {
                ShortUrlDataOut(
                    url = url,
                    properties = mapOf(
                        "safe" to it.properties.safe
                    )
                )
            }
            ResponseEntity<ShortUrlDataOut>(response, h, HttpStatus.CREATED)
        }

    @PostMapping("/api/bulk", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    override fun createCsv(data: CsvDataIn, request: HttpServletRequest): ResponseEntity<String> {
        val csvContent = data.file.bytes.toString(Charsets.UTF_8)
        if (csvContent.isEmpty()) {
            return ResponseEntity(HttpStatus.OK)
        }
        val h = HttpHeaders()
        h.contentType = MediaType.parseMediaType("text/csv")
        val lines = csvContent.split("\n").map { it.trim() }
        // Saltar la primera línea
        lines.drop(1)

        val resultCsv = StringBuilder("URI,URI_Recortada,Mensaje\n")
        var firstUri = true
        for (line in lines) {
            val uri = line.split(",")[0]
            val qr = line.split(",")[1]
            
            val (originalUri, shortenedUri, errorMessage) = shortUrl(uri, data, request)
            if (firstUri) {
                h.location = shortenedUri
                firstUri = false
            }

            if (qr != null) {
                val urlQr = shortenedUri.toString() + "/qr"
                resultCsv.append("$originalUri,$shortenedUri,$urlQr,$errorMessage\n")
            } else {
                resultCsv.append("$originalUri,$shortenedUri,$errorMessage\n")
            }
        }
        return ResponseEntity(resultCsv.toString(), h, HttpStatus.CREATED)
    }

    /**
     * Creates a short url from a [uri].
     * @return a [ShortInfo] with the original uri, the shortened uri and an error message if any.
     */
    private fun shortUrl( uri: String, data: CsvDataIn, request: HttpServletRequest): ShortInfo{
        try {
            val shortUrlDataIn = ShortUrlDataIn(uri, data.sponsor, false)
            val response = createShortUrlUseCase.create(
                url = shortUrlDataIn.url,
                data = ShortUrlProperties(
                    ip = request.remoteAddr,
                    sponsor = shortUrlDataIn.sponsor
                )
            )

            val originalUri = uri
            val shortenedUri = linkTo<UrlShortenerControllerImpl> { redirectTo(response.hash, request) }.toUri()
            val errorMessage = if (response.properties.safe) "" else "ERROR"

            return ShortInfo(originalUri, shortenedUri, errorMessage)
        } catch (e: Exception) {
            val originalUri = uri
            val shortenedUri = URI("")
            val errorMessage = e.message ?: "ERROR"
            return ShortInfo(originalUri, shortenedUri, errorMessage)
        }

    }


}
