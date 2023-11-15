package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.Redirection
import es.unizar.urlshortener.core.InfoHeaders
import es.unizar.urlshortener.core.RedirectionNotFound
import es.unizar.urlshortener.core.InfoHeadersRepositoryService
import eu.bitwalker.useragentutils.UserAgent
import eu.bitwalker.useragentutils.Browser
import eu.bitwalker.useragentutils.OperatingSystem
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap

/**
 * getSumary: 
 * Given a key returns a [MultiValueMap] that contains a [hash] and a [Pair] of [browser] and [opSystem].
 * 
 * logHeader:
 * Given a key and a userAgent, it saves the [opSystem] and [browser] of the user.
 */
interface InfoHeadersUseCase {
    fun getSumary(key: String): MultiValueMap<String, Pair<String, String>>
    fun logHeader(key: String, userAgent: String)
}


/**
 * Implementation of [InfoHeadersUseCase].
 */
class InfoHeadersUseCaseImpl(
    private val infoHeadersRepository: InfoHeadersRepositoryService
) : InfoHeadersUseCase {
    override fun getSumary(key: String) : MultiValueMap<String, Pair<String, String>> {
        val info = infoHeadersRepository.findByKey(key) ?: throw RedirectionNotFound(key)

        val multiValueMap = LinkedMultiValueMap<String, Pair<String, String>>()
        for (infoHeader in info) {
            val id = infoHeader.hash
            val value = Pair(infoHeader.browser, infoHeader.opSystem)

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
        
        infoHeadersRepository.save(InfoHeaders(key, osName, browserName))
    }
}
