@file:Suppress("LongParameterList", "TooGenericExceptionCaught")

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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.multipart.MultipartFile
import es.unizar.urlshortener.core.ShortUrlRepositoryService
import java.io.StringReader
import java.io.StringWriter
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
    fun redirectTo(id: String, request: HttpServletRequest): ResponseEntity<Unit>

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
    fun getQr(id: String, request: HttpServletRequest): ResponseEntity<ByteArray>

    /**
     * Gets the metrics of the system.
     */
    fun getMetrics(request: HttpServletRequest): ResponseEntity<Any>

    /*
    * Gets a specific metric
    * */
    fun getMetric(id: String): ResponseEntity<Any>
    
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
    val shortUrlRepository: ShortUrlRepositoryService,
    val processCsvUseCase: ProcessCsvUseCase,
    val controlador: QueueController,
    val metricsUseCase: MetricsUseCase

) : UrlShortenerController {

    // val cola: BlockingQueue<suspend () -> Unit> = LinkedBlockingQueue()

    // fun insertarComando(funcion: suspend () -> Unit) {
    //     // Inserta el comando en la cola bloqueante
    //     cola.put(funcion)
    //     println("Comando insertado: $funcion")
    // }

    
    // fun takeFromQueue() : suspend () -> Unit {
    //     // var comando: suspend () -> Unit? = null
    //     // try {
    //        // while (true) {
    //             // Bloquea hasta que haya un elemento en la cola
    //     val comando = cola.take()
    //     println("Consumidor ejecutando comando: $comando")

    //             // Aquí puedes agregar la lógica para procesar el comando según tus necesidades
    //             // }
    //     // } catch (e: InterruptedException) {
    //     //         Thread.currentThread().interrupt()
    //     // }
    //     return comando
    // }

    // @Async
    // fun producerMethod(funcion: suspend () -> Unit) = GlobalScope.launch {
    //     insertarComando(funcion)
    // }

    // @Scheduled(fixedRate = 1000)
    // fun consumerMethod() { GlobalScope.launch {
    //     // Consumer logic
    //     println("holaaaaaaaaaaaaaaa")
    //     val comando = takeFromQueue()
    //     // Procesa el comando según tus necesidades
    //     if (comando != null) {
    //         // Procesa el comando según tus necesidades
    //         comando.invoke()
    //     }
    // }
    // }

    @GetMapping("/api/link/{id}")
    override fun getSumary(@PathVariable("id") id: String): ResponseEntity<Sumary> =
        logClickUseCase.getSumary(id).let{
            val response = Sumary(info = it)
            ResponseEntity<Sumary>(response, HttpStatus.OK)
        }

    @GetMapping("/{id:(?!api|index).*}")
    override fun redirectTo(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<Unit> =
        redirectUseCase.redirectTo(id).let {
            // if (request.getHeader("User-Agent") != null) {
            //     infoHeadersUseCase.logHeader(id, request.getHeader("User-Agent"))
            // }
            logClickUseCase.logClick(id, ClickProperties(ip = request.remoteAddr), request.getHeader("User-Agent"))
            val h = HttpHeaders()
            h.location = URI.create(it.target)
            ResponseEntity<Unit>(h, HttpStatus.valueOf(it.mode))
        }

    @GetMapping("/{id}/qr")
    override fun getQr(@PathVariable("id") id: String, request: HttpServletRequest): ResponseEntity<ByteArray> {
        // Verificar si el id existe en la base de datos
        val shortUrl = shortUrlRepository.findByKey(id)
        if (shortUrl != null && shortUrl.properties.qr == true) {
            
            // Obtener la URL completa
            // val requestURL = request.requestURL.toString().substringBeforeLast("/qr")
            // val qrImage = createQrUseCase.generateQRCode(requestURL)
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
        metricsUseCase.registerOperatingSystemMetrics()
        println(metricsUseCase.dumb())
    }
    @GetMapping("/api/metrics")
    override fun getMetrics(request: HttpServletRequest): ResponseEntity<Any> {
        val client = HttpClient.newBuilder().build()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/actuator/metrics"))
            .build();

        val response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if(response.statusCode() == OK){
            return ResponseEntity.ok().header("Content-Type", "application/json")
                .body(response.body())
        }
        // SI NO LO ENCUENTRA ES BAD REQUEST?
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

    }

    @GetMapping("/api/metrics/{id}")
    override fun getMetric(@PathVariable("id") id: String): ResponseEntity<Any> {
        val client = HttpClient.newBuilder().build()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/actuator/metrics/$id"))
            .build();

        val response = client.send(request, HttpResponse.BodyHandlers.ofString());
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
                val urlQr = url.toString() + "/qr"
                // comprobar que headersSumary no es null
                val miFuncion: suspend () -> Unit = {
                    createQrUseCase.generateQRCode(urlQr, it)
                }
                controlador.producerMethod(miFuncion)
                
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
        val lines = CSVReader(StringReader(csvContent)).readAll().map { it.map(String::trim) }

        val h = HttpHeaders().apply { contentType = MediaType.parseMediaType("text/csv") }

        val resultCsv = StringWriter()
        val csvWriter = CSVWriter(resultCsv)
        csvWriter.writeNext(arrayOf("URI", "URI_Recortada", "Mensaje"), false)

        var firstUri = true
        for (i in 1 until lines.size) {
            var line = lines[i]
            val uri = line[0]
            val qr = if (checkQr && line.size > 1 && line[1].isNotBlank()) line[1] else null
            
            val (originalUri, shortenedUri, errorMessage) = shortUrl(uri, data, request)
            if (firstUri) h.location = shortenedUri
            firstUri = false
            
            if (checkQr) { 
                // generar el qr
                var urlQr = ""
                if (errorMessage.isBlank() && qr != null) {
                    urlQr = "$shortenedUri/qr"
                    createShortUrlUseCase.create(
                        url = originalUri,
                        data = ShortUrlProperties(
                            ip = request.remoteAddr,
                            qr = true
                        )
                    )
                }
                
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
