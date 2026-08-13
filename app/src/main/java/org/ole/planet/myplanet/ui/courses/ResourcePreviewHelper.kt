package org.ole.planet.myplanet.ui.courses

import android.media.MediaMetadataRetriever
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.utils.DispatcherProvider
import java.io.File
import java.io.FileReader

class ResourcePreviewHelper(private val dispatcherProvider: DispatcherProvider) {

    suspend fun getAudioPreview(file: File): String {
        return withContext(dispatcherProvider.io) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val totalSeconds = durationMs / 1000
                String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60)
            } catch (e: Exception) {
                ""
            } finally {
                retriever.release()
            }
        }
    }

    suspend fun getCsvPreview(file: File): String? {
        return withContext(dispatcherProvider.io) {
            try {
                val sb = StringBuilder()
                CSVReaderBuilder(FileReader(file))
                    .withCSVParser(CSVParserBuilder().withSeparator(',').withQuoteChar('"').build())
                    .build().use { reader ->
                        var count = 0
                        for (row in reader) {
                            if (count >= 5) break
                            sb.appendLine(row.joinToString("  |  "))
                            count++
                        }
                    }
                sb.toString().trimEnd().takeIf { it.isNotEmpty() }
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
}
