@file:Suppress("WildcardImport")

package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.ClickRepositoryService
import es.unizar.urlshortener.core.ShortUrlRepositoryService
import io.micrometer.core.instrument.MeterRegistry
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
    // private val operatingSystemsCounter: Counter = Counter.builder("operating_systems_count")
    //     .description("Count of Different Operating Systems")
    //     .register(registry)

    // init {
    //     registerOperatingSystemMetrics()
    // }
    // override fun findAllOperatingSystems(): List<String> {
    //     return clickRepositoryService.findAllOperatingSystems().distinct()
    // }

    //private val operatingSystemsCount: MutableMap<String, Int> = mutableMapOf()
    private val log = LoggerFactory.getLogger(this::class.java)

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

    override fun registerShortUrlsCount(){
        val metricUrls = "short.urls.count"
        val count = shortUrlRepositoryService.countShortUrls()
        try {
            registry.removeByPreFilterId(registry.get(metricUrls).meter().id)
            registry.gauge(
                metricUrls,
                count.toDouble()
            )
        } catch (e: io.micrometer.core.instrument.search.MeterNotFoundException) {
            // Handle MeterNotFoundException
            log.debug("Caught MeterNotFoundException: ${e.message}")
        }

        //println("SHORT URLS COUNT $count")
    }

}
