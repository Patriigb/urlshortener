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
            .contentType("application/json")
            .body("names", notNullValue()) // Verifica que 'names' no sea nulo
            .body("names", hasItems(isA(String::class.java))) // Verifica que 'names' contenga solo cadenas

    }

    @Test
    fun `get specific metric jvm memory used and is ok`() {
        val metricName = "jvm.memory.used"

        given()
            .contentType(ContentType.JSON)
            .`when`()
            .get("/api/stats/metrics/$metricName")
            .then()
            .statusCode(HttpStatus.OK.value())
            .contentType("application/json")
            .body("name", equalTo(metricName))


    }

    @Test
    fun `get specific metric returns NOT_FOUND if metric does not exists`() {
        val metricName = "exists"
        given()
            .contentType(ContentType.JSON)
            .`when`()
            .get("/api/stats/metrics/$metricName")
            .then()
            .statusCode(HttpStatus.NOT_FOUND.value())
    }

}
