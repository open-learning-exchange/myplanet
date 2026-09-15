package org.ole.planet.myplanet.utils

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class Sha256Utils {
    companion object {
        private val HEX_CHARS = "0123456789abcdef".toCharArray()
    }

    fun getCheckSumFromFile(file: File): String? {
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
            val result = CharArray(hash.size * 2)
            for (i in hash.indices) {
                val v = hash[i].toInt() and 0xFF
                result[i * 2] = HEX_CHARS[v ushr 4]
                result[i * 2 + 1] = HEX_CHARS[v and 0x0F]
            }
            String(result)
        } catch (_: Exception) {
            null
        }
    }
}
