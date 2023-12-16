package es.unizar.urlshortener

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * The marker that makes this project a Spring Boot application.
 */
@OpenAPIDefinition(info = Info(
    title = "Documentación URLShortener"
))
@SpringBootApplication
class Application

/**
 * The main entry point.
 */
fun main(args: Array<String>) {
    @Suppress("SpreadOperator")
    runApplication<Application>(*args)
}
