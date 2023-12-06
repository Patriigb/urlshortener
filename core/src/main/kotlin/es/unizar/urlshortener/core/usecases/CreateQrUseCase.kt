@file:Suppress("WildcardImport")

package es.unizar.urlshortener.core.usecases

import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import javax.imageio.ImageIO
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import es.unizar.urlshortener.core.RedirectionNotFound
import es.unizar.urlshortener.core.QrNotFound
import es.unizar.urlshortener.core.QrNotReady
import es.unizar.urlshortener.core.ShortUrl
import es.unizar.urlshortener.core.ShortUrlProperties
import es.unizar.urlshortener.core.ShortUrlRepositoryService

const val DEFAULT_MAX = 250
const val DEFAULT_MIN = 0

/**
 * Given a content returns a [ByteArray] that contains a QR code.
 */
interface CreateQrUseCase {
    fun generateQRCode(content: String, su: ShortUrl)
    fun getQrCode(id: String): ShortUrl
}

/**
 * Implementation of [CreateQrUseCase].
 */
class CreateQrUseCaseImpl(
    private val shortUrlRepository: ShortUrlRepositoryService
) : CreateQrUseCase {
    override fun generateQRCode(content: String, su: ShortUrl)  {
        val qrCodeWriter = QRCodeWriter()
        val bitMatrix: BitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, DEFAULT_MAX, DEFAULT_MAX)

        val bufferedImage = BufferedImage(DEFAULT_MAX, DEFAULT_MAX, BufferedImage.TYPE_INT_RGB)
        for (x in DEFAULT_MIN until DEFAULT_MAX) {
            for (y in DEFAULT_MIN until DEFAULT_MAX) {
                bufferedImage.setRGB(x, y, if (bitMatrix[x, y]) Color.BLACK.rgb else Color.WHITE.rgb)
            }
        }

        val byteArrayOutputStream = ByteArrayOutputStream()
        ImageIO.write(bufferedImage, "png", byteArrayOutputStream)

        val id = shortUrlRepository.findByKey(su.hash)?.id

        val newSu = ShortUrl(
            id = id,
            hash = su.hash,
            redirection = su.redirection,
            properties = ShortUrlProperties(
                safe = su.properties.safe,
                ip = su.properties.ip,
                sponsor = su.properties.sponsor,
                qr = su.properties.qr,
                qrImage = byteArrayOutputStream.toByteArray()
            )
        )
        shortUrlRepository.save(newSu)
    }

    override fun getQrCode(id: String): ShortUrl{

        val shortUrl = shortUrlRepository.findByKey(id)

        if(shortUrl == null){
            throw RedirectionNotFound(id)
        } else if(shortUrl.properties.qr != true){
            throw QrNotFound(id)
        } else if(shortUrl.properties.qrImage == null){
            throw QrNotReady(id)
        } else {
            return shortUrl
        }

        
        // if (shortUrl != null && shortUrl.properties.qr == true) {

        //     if(shortUrl.properties.qrImage == null){
        //         val errorResponse = mapOf("error" to "Imagen QR no disponible. Intentalo más tarde.")
        //         return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        //             .header("Retry-After", "5")
        //             .body(errorResponse)
        //     }
            
        //     // Devolver imagen con tipo de contenido correcto
        //     return shortUrl
        //     return ResponseEntity.ok().header("Content-Type", "image/png").body(shortUrl.properties.qrImage)
        // } else {
        //     // Devolver 404 si el id no existe
        //     throw RedirectionNotFound(id)
        //     //return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        // }
    }
}
