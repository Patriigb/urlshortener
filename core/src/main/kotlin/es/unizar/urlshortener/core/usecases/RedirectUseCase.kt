package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.Redirection
import es.unizar.urlshortener.core.InfoHeaders
import es.unizar.urlshortener.core.RedirectionNotFound
import es.unizar.urlshortener.core.ShortUrlRepositoryService
import es.unizar.urlshortener.core.InfoHeadersRepositoryService
import eu.bitwalker.useragentutils.UserAgent
import eu.bitwalker.useragentutils.Browser
import eu.bitwalker.useragentutils.OperatingSystem

/**
 * Given a key returns a [Redirection] that contains a [URI target][Redirection.target]
 * and an [HTTP redirection mode][Redirection.mode].
 *
 * **Note**: This is an example of functionality.
 */
interface RedirectUseCase {
    fun redirectTo(key: String, userAgent: String): Redirection
}

/**
 * Implementation of [RedirectUseCase].
 */
class RedirectUseCaseImpl(
    private val shortUrlRepository: ShortUrlRepositoryService,
    private val infoHeadersRepository: InfoHeadersRepositoryService
) : RedirectUseCase {
    override fun redirectTo(key: String, userAgent: String) : Redirection {
        val userAgentParse = UserAgent.parseUserAgentString(userAgent)
        val browser = userAgentParse.browser
        val operatingSystem = userAgentParse.operatingSystem

        val browserName = browser.name
        val osName = operatingSystem.name
        
        println("Info añadida: " + browserName + " " + osName)
        infoHeadersRepository.save(InfoHeaders(key, "hola", "a"))
        infoHeadersRepository.save(InfoHeaders(key, osName, browserName))
        return shortUrlRepository.findByKey(key)?.redirection?: throw RedirectionNotFound(key)
    } 
}

