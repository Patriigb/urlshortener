package es.unizar.urlshortener 

import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.isA
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UrlShortenerControllerHttpTest {

    @LocalServerPort
    private var port: Int = 0

    @BeforeEach
    fun setUp() {
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = port
    }

    @Test
    fun `gets list of metrics`() {
        given()
            .contentType(ContentType.JSON)
            .`when`()
            .get("/api/stats/metrics")
            .then()
            .statusCode(HttpStatus.OK.value())
            .contentType("application/vnd.spring-boot.actuator.v3+json")
            .body("names", notNullValue()) // Verifica que 'names' no sea nulo
            .body("names", hasItems(isA(String::class.java))) // Verifica que 'names' contenga solo cadenas
    }

    @Test
    fun `get metric jvm threads states and is ok`() {
        val metricName = "jvm.threads.states"

        given()
            .contentType(ContentType.JSON)
            .`when`()
            .get("/api/stats/metrics/$metricName")
            .then()
            .statusCode(HttpStatus.OK.value())
            .contentType("application/vnd.spring-boot.actuator.v3+json")
            .body("name", equalTo(metricName))
    }

    @Test
    fun `get metric process cpu usage and is ok`() {
        val metricName = "process.cpu.usage"

        given()
            .contentType(ContentType.JSON)
            .`when`()
            .get("/api/stats/metrics/$metricName")
            .then()
            .statusCode(HttpStatus.OK.value())
            .contentType("application/vnd.spring-boot.actuator.v3+json")
            .body("name", equalTo(metricName))
    }

    @Test
    fun `get metric operating system count and is ok`() {
        val metricName = "operating.system.count"

        given()
            .contentType(ContentType.JSON)
            .`when`()
            .get("/api/stats/metrics/$metricName")
            .then()
            .statusCode(HttpStatus.OK.value())
            .contentType("application/vnd.spring-boot.actuator.v3+json")
            .body("name", equalTo(metricName))
    }

    @Test
    fun `get metric short url count and is ok`() {
        val metricName = "short.url.count"

        given()
            .contentType(ContentType.JSON)
            .`when`()
            .get("/api/stats/metrics/$metricName")
            .then()
            .statusCode(HttpStatus.OK.value())
            .contentType("application/vnd.spring-boot.actuator.v3+json")
            .body("name", equalTo(metricName))
    }

    @Test
    fun `get a metric that does not exists and returns NOT_FOUND`() {
        val metricName = "exists"
        given()
            .contentType(ContentType.JSON)
            .`when`()
            .get("/api/stats/metrics/$metricName")
            .then()
            .statusCode(HttpStatus.NOT_FOUND.value())
    }
}
