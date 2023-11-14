@file:Suppress("TooManyFunctions")

package es.unizar.urlshortener

import es.unizar.urlshortener.core.usecases.CreateQrUseCaseImpl
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCaseImpl
import es.unizar.urlshortener.core.usecases.InfoHeadersUseCaseImpl
import es.unizar.urlshortener.core.usecases.LogClickUseCaseImpl
import es.unizar.urlshortener.core.usecases.MetricsUseCaseImpl
import es.unizar.urlshortener.core.usecases.ProcessCsvUseCaseImpl
import es.unizar.urlshortener.core.usecases.RedirectUseCaseImpl

import es.unizar.urlshortener.infrastructure.delivery.HashServiceImpl
import es.unizar.urlshortener.infrastructure.delivery.ValidatorServiceImpl
import es.unizar.urlshortener.infrastructure.repositories.ClickEntityRepository
import es.unizar.urlshortener.infrastructure.repositories.InfoHeadersEntityRepository
import es.unizar.urlshortener.infrastructure.repositories.InfoHeadersRepositoryServiceImpl
import es.unizar.urlshortener.infrastructure.repositories.ClickRepositoryServiceImpl
import es.unizar.urlshortener.infrastructure.repositories.ShortUrlEntityRepository
import es.unizar.urlshortener.infrastructure.repositories.ShortUrlRepositoryServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires use cases with service implementations, and services implementations with repositories.
 *
 * **Note**: Spring Boot is able to discover this [Configuration] without further configuration.
 */
@Configuration
class ApplicationConfiguration(
    @Autowired val shortUrlEntityRepository: ShortUrlEntityRepository,
    @Autowired val clickEntityRepository: ClickEntityRepository,
    @Autowired val infoHeadersEntityRepository: InfoHeadersEntityRepository
) {
    @Bean
    fun clickRepositoryService() = ClickRepositoryServiceImpl(clickEntityRepository)

    @Bean
    fun shortUrlRepositoryService() = ShortUrlRepositoryServiceImpl(shortUrlEntityRepository)

    @Bean
    fun infoHeadersRepositoryService() = InfoHeadersRepositoryServiceImpl(infoHeadersEntityRepository)

    @Bean
    fun validatorService() = ValidatorServiceImpl()

    @Bean
    fun hashService() = HashServiceImpl()

    @Bean
    fun redirectUseCase() = RedirectUseCaseImpl(shortUrlRepositoryService())

    @Bean
    fun infoHeadersUseCase() = InfoHeadersUseCaseImpl(infoHeadersRepositoryService())

    @Bean
    fun logClickUseCase() = LogClickUseCaseImpl(clickRepositoryService())
    
    @Bean
    fun createQrUseCase() = CreateQrUseCaseImpl()

    @Bean
    fun processCsvUseCase() = ProcessCsvUseCaseImpl()

    @Bean
    fun metricsUseCase() = MetricsUseCaseImpl()

    @Bean
    fun createShortUrlUseCase() =
    CreateShortUrlUseCaseImpl(shortUrlRepositoryService(), validatorService(), hashService())
    
}
