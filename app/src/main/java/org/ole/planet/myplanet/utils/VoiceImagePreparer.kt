package org.ole.planet.myplanet.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

object VoiceImagePreparer {
    private const val TAG = "VoiceImagePreparer"
    private const val DIR_NAME = "voice_images"
    const val MAX_SIDE = 1280
    const val JPEG_QUALITY = 85
    const val MAX_GIF_BYTES = 2L * 1024 * 1024

    sealed interface Result {
        data class Ready(val path: String) : Result
        data object GifTooLarge : Result
        data object Unreadable : Result
    }

    fun directory(context: Context): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun isOwnedFile(context: Context, file: File): Boolean {
        val parent = file.canonicalFile.parentFile ?: return false
        return parent == File(context.filesDir, DIR_NAME).canonicalFile || parent == context.cacheDir.canonicalFile
    }

    fun prepare(context: Context, uri: Uri, timeProvider: TimeProvider): Result {
        return try {
            if (isGif(context, uri)) copyGif(context, uri, timeProvider) else compress(context, uri, timeProvider)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "prepare ran out of memory", e)
            Result.Unreadable
        } catch (e: Exception) {
            Log.e(TAG, "prepare failed", e)
            Result.Unreadable
        }
    }

    private fun open(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("No stream for $uri")

    private fun isGif(context: Context, uri: Uri): Boolean {
        val header = ByteArray(4)
        val read = open(context, uri).use { it.read(header) }
        return read == 4 && String(header, Charsets.US_ASCII) == "GIF8"
    }

    private fun copyGif(context: Context, uri: Uri, timeProvider: TimeProvider): Result {
        val target = newFile(context, "gif", timeProvider)
        var tooLarge = false
        open(context, uri).use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_GIF_BYTES) {
                        tooLarge = true
                        break
                    }
                    output.write(buffer, 0, n)
                }
            }
        }
        if (tooLarge) {
            target.delete()
            return Result.GifTooLarge
        }
        return Result.Ready(target.absolutePath)
    }

    private fun compress(context: Context, uri: Uri, timeProvider: TimeProvider): Result {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(context, uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return Result.Unreadable

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(max(bounds.outWidth, bounds.outHeight))
        }
        val decoded = open(context, uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: return Result.Unreadable

        val orientation = try {
            open(context, uri).use { ExifInterface(it) }
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            Log.w(TAG, "EXIF read failed", e)
            ExifInterface.ORIENTATION_NORMAL
        }

        val output = render(decoded, orientation)
        if (output !== decoded) decoded.recycle()
        val target = newFile(context, "jpg", timeProvider)
        try {
            val written = target.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (!written) {
                target.delete()
                return Result.Unreadable
            }
        } finally {
            output.recycle()
        }
        return Result.Ready(target.absolutePath)
    }

    internal fun sampleSize(longestSide: Int): Int {
        var sample = 1
        while (longestSide / (sample * 2) >= MAX_SIDE) sample *= 2
        return sample
    }

    internal fun scaleFor(width: Int, height: Int): Float =
        minOf(1f, MAX_SIDE.toFloat() / max(width, height))

    private fun render(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        val swapsSides = when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> { matrix.setScale(-1f, 1f); false }
            ExifInterface.ORIENTATION_ROTATE_180 -> { matrix.setRotate(180f); false }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.setScale(1f, -1f); false }
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f); true }
            ExifInterface.ORIENTATION_ROTATE_90 -> { matrix.setRotate(90f); true }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f); true }
            ExifInterface.ORIENTATION_ROTATE_270 -> { matrix.setRotate(-90f); true }
            else -> false
        }
        val uprightWidth = if (swapsSides) source.height else source.width
        val uprightHeight = if (swapsSides) source.width else source.height
        val scale = scaleFor(uprightWidth, uprightHeight)
        val outWidth = max(1, (uprightWidth * scale).roundToInt())
        val outHeight = max(1, (uprightHeight * scale).roundToInt())

        val transform = Matrix().apply {
            postTranslate(-source.width / 2f, -source.height / 2f)
            postConcat(matrix)
            postScale(scale, scale)
            postTranslate(outWidth / 2f, outHeight / 2f)
        }
        val result = createBitmap(outWidth, outHeight)
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, transform, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return result
    }

    private fun newFile(context: Context, extension: String, timeProvider: TimeProvider): File {
        val dir = directory(context)
        val stamp = timeProvider.now()
        var attempt = 0
        while (true) {
            val name = if (attempt == 0) "post$stamp.$extension" else "post${stamp}_$attempt.$extension"
            val file = File(dir, name)
            if (file.createNewFile()) return file
            attempt++
        }
    }
}
