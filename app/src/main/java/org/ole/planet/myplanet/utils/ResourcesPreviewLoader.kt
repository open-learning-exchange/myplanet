package org.ole.planet.myplanet.utils

import android.media.MediaMetadataRetriever
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.File
import java.io.FileReader
import java.util.Locale
import kotlinx.coroutines.withContext

data class CsvPreview(
    val rows: List<List<String>>,
    val hasMoreRows: Boolean
) {
    val columnCount: Int = rows.maxOfOrNull { it.size } ?: 0
}

class ResourcesPreviewLoader(private val dispatcherProvider: DispatcherProvider) {

    suspend fun getAudioPreview(file: File): String {
        return withContext(dispatcherProvider.io) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val totalSeconds = durationMs / 1000
                String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
            } catch (e: Exception) {
                ""
            } finally {
                retriever.release()
            }
        }
    }

    suspend fun getCsvPreview(file: File, maxRows: Int = CARD_PREVIEW_ROWS): CsvPreview? {
        return withContext(dispatcherProvider.io) {
            try {
                CSVReaderBuilder(FileReader(file))
                    .withCSVParser(CSVParserBuilder().withSeparator(',').withQuoteChar('"').build())
                    .build().use { reader ->
                        val rows = mutableListOf<List<String>>()
                        var hasMoreRows = false
                        for (row in reader) {
                            if (row.all { it.isBlank() }) continue
                            if (rows.size >= maxRows) {
                                hasMoreRows = true
                                break
                            }
                            rows.add(row.map { it.trim() })
                        }
                        if (rows.isEmpty()) {
                            null
                        } else {
                            CsvPreview(rows, hasMoreRows)
                        }
                    }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun getTextPreview(file: File): String? {
        return withContext(dispatcherProvider.io) {
            try {
                file.bufferedReader().useLines { it.take(8).joinToString("\n") }.takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                null
            }
        }
    }

    companion object {
        const val CARD_PREVIEW_ROWS = 6
        const val VIEWER_ROWS = 200
    }
}
