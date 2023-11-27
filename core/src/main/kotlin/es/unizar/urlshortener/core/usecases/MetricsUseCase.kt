@file:Suppress("WildcardImport")

package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.Click
import es.unizar.urlshortener.core.ClickRepositoryService
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Counter
import org.springframework.stereotype.Component
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Meter.Type


// /**
//  * Given a content returns a String that contains metrics.
//  */
interface MetricsUseCase {
    //fun generateMetrics() : JsonContent
    fun getOperatingSystemsCount(osName: String) : Int
    fun registerOperatingSystemMetrics()
    fun dumb() : String


}

// En la implementación MetricsUseCaseImpl
class MetricsUseCaseImpl (
    private val clickRepositoryService: ClickRepositoryService,
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


    override fun registerOperatingSystemMetrics() {
        val operatingSystems = clickRepositoryService.findAllOperatingSystems()
        println("Operating Systems: $operatingSystems")
        val operatingSystemsDistincs = operatingSystems.distinct()
        println("Operating Systems Distinct: $operatingSystemsDistincs")

        for (osName in operatingSystemsDistincs) {
            println("osName: $osName")
            val count = clickRepositoryService.countClicksByOperatingSystem(osName)
            println("count: $count")

            registry.gauge(
                "operating.systems.count",
                Tags.of("os", osName),
                count.toDouble()
            )
        }
    }
    // override fun registerOperatingSystemsMetrics() {
    //     val operatingSystems = clickRepositoryService.findAllOperatingSystems()
    //     operatingSystems.forEach { osName ->
    //         operatingSystemsCounter.tags("os", osName).increment()
    //     }
    // }

    override fun getOperatingSystemsCount(osName: String): Int {
        return clickRepositoryService.countClicksByOperatingSystem(osName)
    }

    override fun dumb(): String {
        return "dumb"
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
