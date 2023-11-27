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

    // override fun getAllClicks(): Iterable<Click>? =
    //     clickEntityRepository.findAll().map { it.toDomain() } 

    override fun countClicksByOperatingSystem(osName: String): Int {
        println("countClicksByOperatingSystem")
        println("osName: $osName")
        println(clickEntityRepository.findAll()
            .filter { it.platform == osName }
            .count())
            
        return clickEntityRepository.findAll()
            .filter { it.platform == osName }
            .count()
        // clickEntityRepository.getAllClicks()
        // return clickEntityRepository.countByOperatingSystem(osName)
    } 
    
    override fun findAllOperatingSystems(): List<String> {
        println("findAllOperatingSystems")
        return clickEntityRepository.findAll().mapNotNull { it.platform }
    } 
}

/**
 * Implementation of the port [ShortUrlRepositoryService].
 */
class ShortUrlRepositoryServiceImpl(
    private val shortUrlEntityRepository: ShortUrlEntityRepository
) : ShortUrlRepositoryService {
    override fun findByKey(id: String): ShortUrl? = shortUrlEntityRepository.findByHash(id)?.toDomain()

    override fun save(su: ShortUrl): ShortUrl = shortUrlEntityRepository.save(su.toEntity()).toDomain()
}


