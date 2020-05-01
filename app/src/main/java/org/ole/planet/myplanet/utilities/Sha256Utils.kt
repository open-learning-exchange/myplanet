package org.ole.planet.myplanet.utilities

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest

class Sha256Utils {

    public fun getCheckSumFromFile(f: File): String {
        val fis = FileInputStream(f)
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        try {
            var readNum: Int
            while (fis.read(buf).also { readNum = it } != -1) {
                bos.write(buf, 0, readNum)
            }
        } catch (ex: IOException) {
        }
        return generateChecksum(bos);
    }

    private fun generateChecksum(data: ByteArrayOutputStream): String {
        try {
            val digest: MessageDigest = MessageDigest.getInstance("SHA-512")
            val hash: ByteArray = digest.digest(data.toByteArray())
            return printableHexString(hash)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ""
    }

    private fun printableHexString(data: ByteArray): String {
        // Create Hex String
        val hexString: StringBuilder = StringBuilder()
        for (aMessageDigest: Byte in data) {
            var h: String = Integer.toHexString(0xFF and aMessageDigest.toInt())
            while (h.length < 2)
                h = "0$h"
            hexString.append(h)
        }
        return hexString.toString()
    }
}