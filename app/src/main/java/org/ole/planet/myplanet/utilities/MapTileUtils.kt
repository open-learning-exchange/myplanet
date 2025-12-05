package org.ole.planet.myplanet.utilities

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object MapTileUtils {
    @JvmStatic
    fun copyAssets(
        context: Context,
        progressCallback: (String, Long, Long) -> Unit
    ): Pair<Boolean, String?> {
        val tiles = arrayOf("dhulikhel.mbtiles", "somalia.mbtiles")
        val assetManager = context.assets
        try {
            val osmdroidDir = context.getExternalFilesDir("osmdroid")
            if (osmdroidDir == null) {
                return Pair(false, "External storage is not available.")
            }
            if (!osmdroidDir.exists()) {
                if (!osmdroidDir.mkdirs()) {
                    return Pair(false, "Failed to create osmdroid directory")
                }
            }

            for (fileName in tiles) {
                val outFile = File(osmdroidDir, fileName)
                try {
                    assetManager.openFd(fileName).use { afd ->
                        val totalBytes = afd.length
                        if (outFile.exists() && outFile.length() == totalBytes) {
                            progressCallback(fileName, totalBytes, totalBytes)
                            continue
                        }

                        assetManager.open(fileName).use { input ->
                            FileOutputStream(outFile).use { output ->
                                copyFile(input, output, totalBytes) { bytesCopied ->
                                    progressCallback(fileName, bytesCopied, totalBytes)
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    // It's possible that the asset is compressed, in which case openFd will fail.
                    // In this case, we can't get the file size, so we'll just copy without progress.
                    if (outFile.exists()) {
                        // If the file exists but we can't verify its size, assume it's complete.
                        continue
                    }
                    assetManager.open(fileName).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            return Pair(true, null)
        } catch (e: Exception) {
            e.printStackTrace()
            return Pair(false, e.message)
        }
    }


    @Throws(IOException::class)
    private fun copyFile(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        progress: (Long) -> Unit
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesCopied: Long = 0
        var read = input.read(buffer)
        while (read != -1) {
            output.write(buffer, 0, read)
            bytesCopied += read
            progress(bytesCopied)
            read = input.read(buffer)
        }
    }
}