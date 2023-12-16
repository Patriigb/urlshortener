package es.unizar.urlshortener.infrastructure.repositories

import es.unizar.urlshortener.core.Click
import es.unizar.urlshortener.core.ClickRepositoryService
import es.unizar.urlshortener.core.ShortUrl
import es.unizar.urlshortener.core.ShortUrlRepositoryService
/**
 * Implementation of the port [ClickRepositoryService].
 */
class ClickRepositoryServiceImpl(
    private val clickEntityRepository: ClickEntityRepository
) : ClickRepositoryService {
    override fun save(cl: Click): Click = clickEntityRepository.save(cl.toEntity()).toDomain()

    override fun findByKey(id: String): Iterable<Click>? = 
    clickEntityRepository.findByHash(id)?.map { it.toDomain() }

    override fun countClicksByOperatingSystem(osName: String): Int {
        return clickEntityRepository.findAll().count { it.platform == osName }
    } 
    
    override fun findAllOperatingSystems(): List<String> {
        return clickEntityRepository.findAll().mapNotNull { it.platform }
    } 
}

/**
 * Implementation of the port [ShortUrlRepositoryService].
 */
class ShortUrlRepositoryServiceImpl(
    private val shortUrlEntityRepository: ShortUrlEntityRepository
) : ShortUrlRepositoryService {
    override fun findByKey(hash: String): ShortUrl? = shortUrlEntityRepository.findByHash(hash)?.toDomain()

    override fun save(su: ShortUrl): ShortUrl = shortUrlEntityRepository.save(su.toEntity()).toDomain()

    override fun countShortUrls () : Int {

        return shortUrlEntityRepository.findAll().count()
    }
}


