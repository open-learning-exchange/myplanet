package org.ole.planet.myplanet.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object MapTileUtils {
    fun copyAssets(context: Context) {
        val tiles = arrayOf("dhulikhel.mbtiles", "somalia.mbtiles")
        val assetManager = context.assets
        for (s in tiles) {
            try {
                val outFile = File(Environment.getExternalStorageDirectory().toString() + "/osmdroid", s)

                if (outFile.exists() && outFile.length() > 0) {
                    continue
                }

                outFile.parentFile?.mkdirs()

                assetManager.open(s).use { input ->
                    FileOutputStream(outFile).use { output ->
                        copyFile(input, output)
                    }
                }
            } catch (e: Exception) {
                Log.w("MapTileUtils", "Failed to copy map tile: $s", e)
            }
        }
    }

    @Throws(IOException::class)
    private fun copyFile(`in`: InputStream, out: OutputStream) {
        `in`.copyTo(out)
    }
}
