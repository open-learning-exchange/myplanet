package org.ole.planet.myplanet.utilities

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object MapTileUtils {
    @JvmStatic
    fun copyAssets(context: Context) {
        val tiles = arrayOf("dhulikhel.mbtiles", "somalia.mbtiles")
        val assetManager = context.assets
        try {
            for (s in tiles) {
                val outFile = File(Environment.getExternalStorageDirectory().toString() + "/osmdroid", s)
                assetManager.open(s).use { input ->
                    FileOutputStream(outFile).use { output ->
                        copyFile(input, output)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun copyFile(`in`: InputStream, out: OutputStream) {
        `in`.copyTo(out)
    }
}
