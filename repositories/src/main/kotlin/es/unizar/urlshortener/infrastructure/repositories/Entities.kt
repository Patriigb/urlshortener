@file:Suppress("WildcardImport")

package es.unizar.urlshortener.infrastructure.repositories

import jakarta.persistence.*
import java.time.OffsetDateTime

/**
 * The [ClickEntity] entity logs clicks.
 */
@Entity
@Table(name = "click")
@Suppress("LongParameterList")
class ClickEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: Long?,
    val hash: String,
    val created: OffsetDateTime,
    val ip: String?,
    val referrer: String?,
    val browser: String?,
    val platform: String?,
    val country: String?
)

/**
 * The [ShortUrlEntity] entity stores short urls.
 */
@Entity
@Table(name = "shorturl")
@Suppress("LongParameterList")
class ShortUrlEntity(
    @Id @GeneratedValue(strategy = GenerationType.AUTO) 
    val id: Long? = null,

    @Column(unique = true)
    val hash: String,
    val target: String,
    val sponsor: String?,
    val created: OffsetDateTime,
    val owner: String?,
    val mode: Int,
    val safe: Boolean,
    val ip: String?,
    val country: String?,
    val qr: Boolean?,

    @Column(length = 6000)
    val qrImage: ByteArray?
)

// /**
//  * The [InfoHeadersEntity] entity stores headers information.
//  */
// @Entity
// @Table(name = "infoheaders")
// @Suppress("LongParameterList")
// class InfoHeadersEntity(
//     @Id
//     @GeneratedValue(strategy = GenerationType.AUTO)
//     val id: Long?,
//     val hash: String,
//     val opSystem: String,
//     val browser: String
// )
