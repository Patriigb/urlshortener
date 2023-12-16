@file:Suppress("WildcardImport")

package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.ClickRepositoryService
import es.unizar.urlshortener.core.ShortUrlRepositoryService
import io.micrometer.core.instrument.*
import org.slf4j.LoggerFactory


// /**
//  * Given a content returns a String that contains metrics.
//  */
interface MetricsUseCase {
    fun registerOperatingSystemMetrics()

    fun registerShortUrlsCount()

}

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

    /*
    override fun registerOperatingSystemMetrics() {
        val operatingSystems = clickRepositoryService.findAllOperatingSystems()
        val operatingSystemsDistinct = operatingSystems.distinct()
        val total = operatingSystemsDistinct.count()
        val metricTotal = "operating.system.count"

        try {
            registry.removeByPreFilterId(registry.get(metricTotal).meter().id)
            for (so in operatingSystemsDistinct) {
                val nameMetric = "operating.system.count.$so"
                registry.removeByPreFilterId(registry.get(nameMetric).meter().id)
            }
        } catch (e: io.micrometer.core.instrument.search.MeterNotFoundException) {
            // Handle MeterNotFoundException
            log.debug("Caught MeterNotFoundException: ${e.message}")
        }

        //println("Total: $total")
        registry.gauge(
            metricTotal,
            total.toDouble()
        )
        for (osName in operatingSystemsDistinct) {
            val count = clickRepositoryService.countClicksByOperatingSystem(osName)
            println("osName: $osName, count: $count")
            registry.gauge(
                "$metricTotal.$osName",
                count.toDouble()
            )
        }
    }
     */

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
