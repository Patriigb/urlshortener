package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.Redirection
import es.unizar.urlshortener.core.InfoHeaders
import es.unizar.urlshortener.core.RedirectionNotFound
import es.unizar.urlshortener.core.ShortUrlRepositoryService
import es.unizar.urlshortener.core.InfoHeadersRepositoryService
import eu.bitwalker.useragentutils.UserAgent
import eu.bitwalker.useragentutils.Browser
import eu.bitwalker.useragentutils.OperatingSystem
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap

/**
 * Given a key returns a [Redirection] that contains a [URI target][Redirection.target]
 * and an [HTTP redirection mode][Redirection.mode].
 *
 * **Note**: This is an example of functionality.
 */
interface InfoHeadersUseCase {
    fun getSumary(key: String): MultiValueMap<String, Pair<String, String>>
    fun logHeader(key: String, userAgent: String)
}


/**
 * Implementation of [GetSumaryUseCase].
 */
class InfoHeadersUseCaseImpl(
    private val infoHeadersRepository: InfoHeadersRepositoryService
) : InfoHeadersUseCase {
    override fun getSumary(key: String) : MultiValueMap<String, Pair<String, String>> {
        //Iterable<InfoHeaders>
        val info = infoHeadersRepository.findByKey(key) ?: throw RedirectionNotFound(key)

        val multiValueMap = LinkedMultiValueMap<String, Pair<String, String>>()
        for (infoHeader in info) {
            val id = infoHeader.hash
            val browser = infoHeader.browser ?: "Unknown"
            val opSystem = infoHeader.opSystem ?: "Unknown"
            val value = Pair(browser, opSystem)

            multiValueMap.add(id, value)
        }
        return multiValueMap
    } 

    override fun logHeader(key: String, userAgent: String) {
        val userAgentParse = UserAgent.parseUserAgentString(userAgent)
        val browser = userAgentParse.browser
        val operatingSystem = userAgentParse.operatingSystem

        val browserName = browser.name
        val osName = operatingSystem.name
        
        println("Info añadida: " + browserName + " " + osName)
        infoHeadersRepository.save(InfoHeaders(key, osName, browserName))
    }
}

