package org.ole.planet.myplanet.utilities

import de.rtner.misc.BinTools
import de.rtner.security.auth.spi.PBKDF2Engine
import de.rtner.security.auth.spi.PBKDF2Parameters
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AndroidDecrypter {
    companion object {

        @JvmStatic
        @Throws(Exception::class)
        fun encrypt(plainText: String, key: String?, iv: String?): String {
            val clean = plainText.toByteArray()
            val ivSize = 16
            val ivBytes = ByteArray(ivSize)
            iv?.let { hexStringToByteArray(it) }?.let { System.arraycopy(it, 0, ivBytes, 0, ivBytes.size) }
            val ivParameterSpec = IvParameterSpec(ivBytes)
            val keyBytes = ByteArray(32)
            key?.let { hexStringToByteArray(it) }?.let { System.arraycopy(it, 0, keyBytes, 0, keyBytes.size) }
            val secretKeySpec = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
            val encrypted = cipher.doFinal(clean)
            val encryptedIVAndText = ByteArray(ivSize + encrypted.size)
            System.arraycopy(ivBytes, 0, encryptedIVAndText, 0, ivSize)
            System.arraycopy(encrypted, 0, encryptedIVAndText, ivSize, encrypted.size)
            return bytesToHex(encrypted)
        }

        @JvmStatic
        fun hexStringToByteArray(s: String): ByteArray {
            val len = s.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }

        @JvmStatic
        private fun bytesToHex(hashInBytes: ByteArray): String {
            val sb = StringBuilder()
            for (b in hashInBytes) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        }

        @JvmStatic
        fun decrypt(encrypted: String?, key: String?, initVector: String?): String? {
            try {
                val iv = IvParameterSpec(initVector?.let { hexStringToByteArray(it) })
                val skeySpec = SecretKeySpec(key?.let { hexStringToByteArray(it) }, "AES")

                val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
                cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv)
                val original = cipher.doFinal(encrypted?.let { hexStringToByteArray(it) })
                return String(original)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return null
        }

        @JvmStatic
        fun androidDecrypter(usrId: String?, usrRawPwd: String?, dbPwdKeyValue: String?, dbSalt: String?): Boolean {
            try {
                val p = PBKDF2Parameters("HmacSHA1", "utf-8", dbSalt?.toByteArray(), 10)
                val dk = PBKDF2Engine(p).deriveKey(usrRawPwd, 20)
                println("$usrId Value ${BinTools.bin2hex(dk).lowercase(Locale.ROOT)}")
                return dbPwdKeyValue.equals(BinTools.bin2hex(dk).lowercase(Locale.ROOT), ignoreCase = true)

            } catch (e: Exception) {
                e.printStackTrace()
            }
            return false
        }

        @JvmStatic
        fun generateIv(): String {
            try {
                val iv = ByteArray(16)
                val random = SecureRandom()
                random.nextBytes(iv)
                return bytesToHex(iv)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return ""
        }

        @JvmStatic
        fun generateKey(): String? {
            val keyGenerator: KeyGenerator
            val secretKey: SecretKey
            try {
                keyGenerator = KeyGenerator.getInstance("AES")
                keyGenerator.init(256)
                secretKey = keyGenerator.generateKey()
                val binary = secretKey.encoded
                return bytesToHex(binary)
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            }
            return null
        }
    }
}