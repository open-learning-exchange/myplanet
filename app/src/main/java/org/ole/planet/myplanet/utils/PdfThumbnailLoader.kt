package org.ole.planet.myplanet.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.core.graphics.createBitmap
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.withContext

object PdfThumbnailLoader {
    private const val DISK_DIR = "pdf_thumbnails"
    private const val DISK_MAX_BYTES = 30L * 1024 * 1024
    private const val JPEG_QUALITY = 85

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024
    }

    suspend fun firstPageBitmap(file: File, dispatcherProvider: DispatcherProvider, targetWidthPx: Int, cacheDir: File? = null): Bitmap? {
        if (targetWidthPx <= 0) return null
        return withContext(dispatcherProvider.io) {
            val cacheKey = "${file.absolutePath}_${file.lastModified()}_${file.length()}_$targetWidthPx"
            cache.get(cacheKey)?.let { return@withContext it }
            val diskFile = cacheDir?.let { diskFile(it, cacheKey) }
            val bitmap = diskFile?.let { readFromDisk(it) } ?: renderFirstPage(file, targetWidthPx)?.also { rendered ->
                diskFile?.let { writeToDisk(it, rendered) }
            }
            bitmap?.also { cache.put(cacheKey, it) }
        }
    }

    internal fun diskFile(cacheDir: File, cacheKey: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(cacheKey.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(File(cacheDir, DISK_DIR), "$hash.jpg")
    }

    private fun renderFirstPage(file: File, targetWidthPx: Int): Bitmap? = try {
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

    private fun readFromDisk(diskFile: File): Bitmap? {
        if (!diskFile.isFile) return null
        return try {
            BitmapFactory.decodeFile(diskFile.path)?.also { diskFile.setLastModified(System.currentTimeMillis()) }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeToDisk(diskFile: File, bitmap: Bitmap) {
        try {
            val dir = diskFile.parentFile ?: return
            if (!dir.isDirectory && !dir.mkdirs()) return
            val tmp = File(dir, "${diskFile.name}.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (!tmp.renameTo(diskFile)) tmp.delete()
            trimDisk(dir)
        } catch (_: Exception) { }
    }

    internal fun trimDisk(dir: File, maxBytes: Long = DISK_MAX_BYTES) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".jpg") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return

        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val size = f.length()
            if (f.delete()) total -= size
        }
    }

    fun evictAll() {
        cache.evictAll()
    }
}
