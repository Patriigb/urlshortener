@file:Suppress("WildcardImport")

package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.*
import java.net.URI



/**
 * Given an url returns the key that is used to create a short URL.
 * When the url is created optional data may be added.
 *
 * **Note**: This is an example of functionality.
 */
interface CreateShortUrlUseCase {
    fun create(url: String, data: ShortUrlProperties): ShortUrl
}

/**
 * Implementation of [CreateShortUrlUseCase].
 */
class CreateShortUrlUseCaseImpl(
    private val shortUrlRepository: ShortUrlRepositoryService,
    private val validatorService: ValidatorService,
    private val hashService: HashService,
    // private val createQrUseCase: CreateQrUseCase
) : CreateShortUrlUseCase {
    override fun create(url: String, data: ShortUrlProperties): ShortUrl {
        if (validatorService.isValid(url)) {
           // var qrImage: ByteArray? = null
            val hash: String = hashService.hasUrl(url)
            val id = shortUrlRepository.findByKey(hash)?.id


            var qr = shortUrlRepository.findByKey(hash)?.properties?.qr ?: false
            var qrImage = shortUrlRepository.findByKey(hash)?.properties?.qrImage

            var dataQr = data.qr ?: false
            qr = qr or dataQr

            val su = ShortUrl(
                id = id,
                hash = hash,
                redirection = Redirection(target = url),
                properties = ShortUrlProperties(
                    safe = data.safe,
                    ip = data.ip,
                    sponsor = data.sponsor,
                    qr = qr,
                    qrImage = qrImage
                )
            )
            shortUrlRepository.save(su)
        
            return su
        } else {
            throw InvalidUrlException(url)
        }
    }
}
