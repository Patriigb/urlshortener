@file:Suppress("WildcardImport")

package es.unizar.urlshortener.core.usecases

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import javax.imageio.ImageIO
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream

const val DEFAULT_MAX = 250
const val DEFAULT_MIN = 0

interface CreateQrUseCase {
    fun generateQRCode(content: String) : ByteArray
}

class CreateQrUseCaseImpl : CreateQrUseCase {
    override fun generateQRCode(content: String) : ByteArray {
        // definir max y min
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
        return byteArrayOutputStream.toByteArray()
    }
}
