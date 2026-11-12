package org.ole.planet.myplanet.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.core.graphics.createBitmap
import java.io.File
import kotlinx.coroutines.withContext

object PdfThumbnailLoader {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024
    }

    suspend fun firstPageBitmap(file: File, dispatcherProvider: DispatcherProvider, targetWidthPx: Int): Bitmap? {
        if (targetWidthPx <= 0) return null
        return withContext(dispatcherProvider.io) {
            val cacheKey = "${file.absolutePath}_${file.lastModified()}_${file.length()}_$targetWidthPx"
            cache.get(cacheKey)?.let { return@withContext it }
            val bitmap = try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        renderer.openPage(0).use { page ->
                            val (width, height) = computePdfRenderSize(page.width, page.height, targetWidthPx)
                            createBitmap(width, height).also { bitmap ->
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                null
            }
            bitmap?.also { cache.put(cacheKey, it) }
        }
    }

    fun evictAll() {
        cache.evictAll()
    }
}
