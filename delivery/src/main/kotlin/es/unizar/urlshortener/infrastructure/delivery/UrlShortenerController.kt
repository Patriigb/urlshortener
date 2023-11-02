package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.ShortUrlProperties
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import es.unizar.urlshortener.core.usecases.CreateQrUseCase
import es.unizar.urlshortener.core.usecases.GetSumaryUseCase
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
}

data class Sumary(
    val info: MultiValueMap<String, Pair<String, String>> =  LinkedMultiValueMap()
)

/**
 * Data required to create a short url.
 */
data class ShortUrlDataIn(
    val url: String,
    val sponsor: String? = null
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
    val getSumaryUseCase: GetSumaryUseCase
) : UrlShortenerController {

    @GetMapping("/api/link/{id}")
    override fun getSumary(@PathVariable("id") id: String): ResponseEntity<Sumary> =
        getSumaryUseCase.getSumary(id).let{
            val response = Sumary(info = it)
            ResponseEntity<Sumary>(response, HttpStatus.CREATED)
        }

    @GetMapping("/{id:(?!api|index).*}")
    override fun redirectTo(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<Unit> =
        redirectUseCase.redirectTo(id, request.getHeader("User-Agent")).let {
            logClickUseCase.logClick(id, ClickProperties(ip = request.remoteAddr))
            val h = HttpHeaders()
            h.location = URI.create(it.target)
            ResponseEntity<Unit>(h, HttpStatus.valueOf(it.mode))
        }

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
            println("hS: " + headersSumary)
            h.location = url
            // url del qr más /qr
            //val urlQr = url.toString() + "/qr"
            val qrUrl = createQrUseCase.generateQRCode(url.toString(), "./qr.png")
            val response = ShortUrlDataOut(
                url = url,
                properties = mapOf(
                    "safe" to it.properties.safe,
                    "sumary" to headersSumary
                ),
                qr = qrUrl
            )
            ResponseEntity<ShortUrlDataOut>(response, h, HttpStatus.CREATED)
        }

    @PostMapping("/api/bulk", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun bulkShortener(@RequestPart("file") file: MultipartFile, 
        @RequestParam("sponsor", required = false) sponsor: String?,
        request: HttpServletRequest): ResponseEntity<String> {
        val csvContent = file.bytes.toString(Charsets.UTF_8)
        val lines = csvContent.split("\n").map { it.trim() }
        val resultCsv = StringBuilder("URI,URI_Recortada,Mensaje\n")

        for (line in lines) {
            val uri = line
            val shortUrlDataIn = ShortUrlDataIn(uri, sponsor)
            val response = createShortUrlUseCase.create(
                url = shortUrlDataIn.url,
                data = ShortUrlProperties(
                    ip = request.remoteAddr,
                    sponsor = shortUrlDataIn.sponsor
                )
            )

            val originalUri = uri
            val shortenedUri = linkTo<UrlShortenerControllerImpl> { redirectTo(response.hash, request) }.toUri()
            val errorMessage = if (response.properties.safe) "OK" else "ERROR"

            resultCsv.append("$originalUri,$shortenedUri,$errorMessage\n")
        }

        return ResponseEntity.ok(resultCsv.toString())
    }
}
