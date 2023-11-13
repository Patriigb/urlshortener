      
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

const val BAD_REQUEST = 400
const val OK = 200

data class CsvContent (
    val result: Int, 
    val content: List<String> = listOf<String>()
)

interface ProcessCsvUseCase {
    fun checkCsvContent(csvContent: String) : CsvContent
}

class ProcessCsvUseCaseImpl : ProcessCsvUseCase {
    override fun checkCsvContent(csvContent: String) : CsvContent {
        // Comprobaciones del csv
        var result: CsvContent
        if (csvContent.isEmpty()) {
            result = CsvContent(OK)
        } else {

            val lines = csvContent.split("\n").map { it.trim() }
            result = CsvContent(OK, lines)

            if (lines.size < 1 || lines[0].split(",")[0] != "URI") {
                return CsvContent(BAD_REQUEST)
            }

            if (lines[0].split(",").size > 1 && lines[0].split(",")[1] == "QR") {
                result = CsvContent(1, lines)
            } 
        }
        return result
    }
}
