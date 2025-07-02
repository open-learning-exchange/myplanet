package org.ole.planet.myplanet.utilities

import com.opencsv.CSVWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException

object CsvUtils {
    @JvmStatic
    fun writeCsv(filePath: String, header: Array<String>, data: List<Array<String>>) {
        try {
            val file = File(filePath)
            file.parentFile?.mkdirs()
            CSVWriter(FileWriter(file)).use { writer ->
                writer.writeNext(header)
                data.forEach { row ->
                    writer.writeNext(row)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
