@file:Suppress("WildcardImport")

package es.unizar.urlshortener.core.usecases

//import com.github.kittinunf.fuel.httpGet

/**
 * Given a content returns a String that contains metrics.
 */
interface MetricsUseCase {
    //fun generateMetrics() : JsonContent
    fun getMetric(content: String) : String

}

data class JsonContent (
    val result: Int,
    val content: List<String> = listOf<String>()
)

class  MetricsUseCaseImpl : MetricsUseCase {
    /* override fun generateMetrics(): JsonContent {
        val metricsUrl = "http://localhost:8080/api/metrics" // Reemplaza esto con tu URL real

        // Realiza la solicitud HTTP
        val (_, response, result) = metricsUrl.httpGet()
            .responseString()

        // Verifica si la solicitud fue exitosa (código de estado 200)
        if (response.statusCode == 200) {
            // Devuelve el JSON como String
            if(result.Success){
                // Devuelve el JSON como String si la solicitud fue exitosa
                return JsonContent(OK, result.get())
            }
            else if (result.Failure){
                // Devuelve un mensaje de error si la solicitud no fue exitosa
                return JsonContent(BAD_REQUEST)

            }
        }
        // Devuelve un mensaje de error si la solicitud no fue exitosa
        return JsonContent(BAD_REQUEST)
    }
    # Spring Datasource
spring:
  datasource:
    url: jdbc:hsqldb:mem:.
    username: sa
    password:
    driverClassName: org.hsqldb.jdbc.JDBCDriver
  jpa:
    open-in-view: false
management:
  endpoints:
    web:
      exposure:
        include: "*"
      base-path: /api
      path-mapping:
        metrics: /metrics
     */

    override fun getMetric(content: String): String {
        return "hola"
    }
}
