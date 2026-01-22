package org.ole.planet.myplanet.utils

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class Sha256Utils {
    fun getCheckSumFromFile(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-512")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val hash = digest.digest()
            hash.joinToString(separator = "") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
