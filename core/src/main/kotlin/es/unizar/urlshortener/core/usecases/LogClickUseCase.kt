package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.Click
import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.ClickRepositoryService
import es.unizar.urlshortener.core.ShortUrlRepositoryService
import es.unizar.urlshortener.core.RedirectionNotFound
import es.unizar.urlshortener.core.QueueController
import eu.bitwalker.useragentutils.UserAgent
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap

/**
 * Log that somebody has requested the redirection identified by a key.
 *
 * **Note**: This is an example of functionality.
 */
interface LogClickUseCase {
    fun logClick(key: String, data: ClickProperties, userAgent: String?)
    fun getSumary(key: String) : MultiValueMap<String, Pair<String, String>>
}

/**
 * Implementation of [LogClickUseCase].
 */
class LogClickUseCaseImpl(
    private val clickRepository: ClickRepositoryService,
    private val shortUrlRepository: ShortUrlRepositoryService,
    private val queueController: QueueController
) : LogClickUseCase {
    override fun getSumary(key: String) : MultiValueMap<String, Pair<String, String>> {
        println("key: " + key)
        shortUrlRepository.findByKey(key)?.redirection?: throw RedirectionNotFound(key)

        val info = clickRepository.findByKey(key) ?: throw RedirectionNotFound(key)

        val multiValueMap = LinkedMultiValueMap<String, Pair<String, String>>()
        for (click in info) {
            val id = click.hash
            val browser = click.properties.browser ?: "Unknown Browser"
            val platform = click.properties.platform ?: "Unknown Platform"

            val value = Pair(browser, platform)

            multiValueMap.add(id, value)
        }
        return multiValueMap
    } 

    private fun saveLog(cl: Click){
        queueController.producerMethod("saveLog") {
            clickRepository.save(cl)
        }
    }

    override fun logClick(key: String, data: ClickProperties, userAgent: String?) {
        if(userAgent != null){
            val userAgentParse = UserAgent.parseUserAgentString(userAgent)
            val browser = userAgentParse.browser
            val operatingSystem = userAgentParse.operatingSystem

            val browserName = browser.name
            val osName = operatingSystem.name
            val cl = Click(
                hash = key,
                properties = ClickProperties(
                    ip = data.ip,
                    browser = browserName,
                    platform = osName
                )
            )
            saveLog(cl)
        }
        else{
            val cl = Click(
                hash = key,
                properties = ClickProperties(
                    ip = data.ip
                )
            )
            clickRepository.save(cl)
        }
    }
}
