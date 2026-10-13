package org.ole.planet.myplanet.utils

import android.media.MediaMetadataRetriever
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.File
import java.io.FileReader
import java.util.Locale
import kotlinx.coroutines.withContext

class ResourcesPreviewLoader(private val dispatcherProvider: DispatcherProvider) {

    private data class CacheKey(
        val path: String,
        val lastModified: Long,
        val length: Long
    )

    private class LruCache<K, V>(private val maxSize: Int) : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    private val audioCache = LruCache<CacheKey, String>(MAX_CACHE_SIZE)
    private val csvCache = LruCache<CacheKey, String>(MAX_CACHE_SIZE)
    private val textCache = LruCache<CacheKey, String>(MAX_CACHE_SIZE)

    suspend fun getAudioPreview(file: File): String {
        return withContext(dispatcherProvider.io) {
            val key = CacheKey(file.absolutePath, file.lastModified(), file.length())
            synchronized(audioCache) { audioCache[key] }?.let { return@withContext it }

            val result = try {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val totalSeconds = durationMs / 1000
                    String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
                } finally {
                    retriever.release()
                }
            } catch (e: Exception) {
                ""
            }

            if (result.isNotEmpty()) {
                synchronized(audioCache) { audioCache[key] = result }
            }
            result
        }
    }

    suspend fun getCsvPreview(file: File): String? {
        return withContext(dispatcherProvider.io) {
            val key = CacheKey(file.absolutePath, file.lastModified(), file.length())
            synchronized(csvCache) { csvCache[key] }?.let { return@withContext it }

            val result = try {
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

            if (result != null) {
                synchronized(csvCache) { csvCache[key] = result }
            }
            result
        }
    }

    suspend fun getTextPreview(file: File): String? {
        return withContext(dispatcherProvider.io) {
            val key = CacheKey(file.absolutePath, file.lastModified(), file.length())
            synchronized(textCache) { textCache[key] }?.let { return@withContext it }

            val result = try {
                file.bufferedReader().useLines { it.take(8).joinToString("\n") }.takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                null
            }

            if (result != null) {
                synchronized(textCache) { textCache[key] = result }
            }
            result
        }
    }

    companion object {
        const val MAX_CACHE_SIZE = 64
    }
}
