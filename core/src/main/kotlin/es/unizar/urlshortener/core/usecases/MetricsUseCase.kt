@file:Suppress("WildcardImport")

package es.unizar.urlshortener.core.usecases

/**
 * Given a content returns a String that contains metrics.
 */
interface MetricsUseCase {
    fun generateMetrics() : String
    fun getMetric(content: String) : String

}

class  MetricsUseCaseImpl : MetricsUseCase {
    override fun generateMetrics(): String {
        val metricsUrl = "http://localhost:8080/actuator/metrics" // Reemplaza esto con tu URL real

        return metricsUrl
    }

    override fun getMetric(content: String): String {
        return "hola"
    }
}
