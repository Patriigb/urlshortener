@file:Suppress("LongParameterList")
package es.unizar.urlshortener.core.usecases

import com.opencsv.CSVReader
import es.unizar.urlshortener.core.ShortUrlProperties
import java.io.StringReader
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

const val BAD_REQUEST = 400
const val OK = 200

/**
 * Data class that contains the result of the csv processing.
 */
data class CsvContent (
    val result: Int, 
    val content: List<String> = listOf<String>()
)

/**
 * Given a csv content returns a [CsvContent] that contains a list of url lines.
 */
interface ProcessCsvUseCase {
    fun checkCsvContent(csvContent: String) : CsvContent
}

/**
 * Implementation of [ProcessCsvUseCase].
 */
class ProcessCsvUseCaseImpl : ProcessCsvUseCase {
    override fun checkCsvContent(csvContent: String) : CsvContent {
        if (csvContent.isEmpty()) {
            return CsvContent(OK)
        }

        val lines = CSVReader(StringReader(csvContent)).readAll().map { it.map(String::trim) }

        return when {
            lines.isEmpty() || lines[0][0] != "URI" -> CsvContent(BAD_REQUEST)
            lines[0].size > 1 && lines[0][1] == "QR" -> CsvContent(1, lines.map { it.joinToString(",") })
            else -> CsvContent(0, lines.map { it.joinToString(",") })
        }
    }
}
