@file:Suppress("WildcardImport")

package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.ClickRepositoryService
import es.unizar.urlshortener.core.ShortUrlRepositoryService
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry


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


    override fun registerOperatingSystemMetrics() {
        val operatingSystems = clickRepositoryService.findAllOperatingSystems()
        // println("Operating Systems: $operatingSystems")
        val operatingSystemsDistinct = operatingSystems.distinct()
        // println("Operating Systems Distinct: $operatingSystemsDistincs")
        val total = operatingSystemsDistinct.count()
        val metricTotal = "operating.system.count"

        try {
            registry.removeByPreFilterId(registry.get(metricTotal).meter().id)
            for (so in operatingSystemsDistinct) {
                val nameMetric = "operating.system.count.$so"
                registry.removeByPreFilterId(registry.get(nameMetric).meter().id)
            }
        } catch (e: Exception) {
            // Manejo de otras excepciones
            // println("Se produjo un error inesperado: ${e.message}")
        }

        println("Total: $total")
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
        } catch (e: Exception) {
            // Manejo de otras excepciones
            // println("Se produjo un error inesperado: ${e.message}")
        }

        println("SHORT URLS COUNT $count")
    }

}



// class MetricsUseCaseImpl(
//     // private val registry: MeterRegistry
//     private val clickRepository: ClickRepositoryService
// ) : MetricsUseCase {
//   // class MetricsController(registry: MeterRegistry) {

//     // private val operatingSystemsCounter: Counter = Counter.builder("operating_systems_count")
//     //     .tag("version", "v1")
//     //     .description("Count of Different Operating Systems")
//     //     .register(registry)

//     // override fun registerOperatingSystem(osName: String) {
//     //     operatingSystemsCounter.tags("os", osName).increment()
//     // }

//     override fun getOperatingSystemsCount(osName: String): Int {
//         // Supongamos que ClickRepository tiene un método para contar clics por sistema operativo
//         return clickRepository.countByOperatingSystem(osName)
//     }
// }
