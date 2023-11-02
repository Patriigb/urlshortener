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
interface GetSumaryUseCase {
    fun getSumary(key: String): MultiValueMap<String, Pair<String, String>>
}


/**
 * Implementation of [GetSumaryUseCase].
 */
class GetSumaryUseCaseImpl(
    private val infoHeadersRepository: InfoHeadersRepositoryService
) : GetSumaryUseCase {
    override fun getSumary(key: String) : MultiValueMap<String, Pair<String, String>> {
        //Iterable<InfoHeaders>
        val info = infoHeadersRepository.findByKey(key) ?: throw RedirectionNotFound(key)

        val multiValueMap = LinkedMultiValueMap<String, Pair<String, String>>()
        for (infoHeader in info) {
            val id = infoHeader.hash
            val value = Pair(infoHeader.browser, infoHeader.opSystem)

            multiValueMap.add(id, value)
        }
        return multiValueMap
    } 
}

