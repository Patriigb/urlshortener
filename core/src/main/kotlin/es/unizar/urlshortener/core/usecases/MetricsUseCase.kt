@file:Suppress("WildcardImport")

package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.ClickRepositoryService
import es.unizar.urlshortener.core.ShortUrlRepositoryService
import io.micrometer.core.instrument.*
import org.slf4j.LoggerFactory


/**
 * Given a content returns a String that contains metrics.
 */
interface MetricsUseCase {
    fun registerOperatingSystemMetrics()

    fun registerShortUrlsCount()
}

/**
 * Implementation of [MetricsUseCase].
 */
class MetricsUseCaseImpl (
    private val clickRepositoryService: ClickRepositoryService,
    private val shortUrlRepositoryService: ShortUrlRepositoryService,
    private val registry: MeterRegistry
) : MetricsUseCase {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun registerOperatingSystemMetrics() {
        val metricSO = "operating.system.count"
        val operatingSystems = clickRepositoryService.findAllOperatingSystems()
        val operatingSystemsDistinct = operatingSystems.distinct()
        // Numero total de SO distintos
        val total = operatingSystemsDistinct.count()

        // Valor actual del contador
        val contador = registry.find(metricSO).counter()
        val valueCounter = contador?.count() ?: 0.0

        // incrementamos el valor del numero de sistemas operativos distintos
        val dif = total - valueCounter
        contador?.increment(dif)

        if (contador != null) {
            log.info("Valor del counter $metricSO: ${contador.count()}")
        }
    }

    override fun registerShortUrlsCount(){
        val metricUrls = "short.url.count"
        val tam = shortUrlRepositoryService.countShortUrls()
        val counter = registry.find(metricUrls).counter()
        val valueCounter = counter?.count() ?: 0.0
        val dif = tam - valueCounter
        counter?.increment(dif)
        if (counter != null) {
            log.info("Valor del counter short.urls.count: ${counter.count()}")
        }
    }

}
