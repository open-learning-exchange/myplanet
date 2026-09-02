package org.ole.planet.myplanet.utils

import de.rtner.security.auth.spi.PBKDF2Engine
import de.rtner.security.auth.spi.PBKDF2Parameters
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AndroidDecrypter {
    companion object {
        private val HEX_CHARS = "0123456789abcdef".toCharArray()

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
            return bytesToHex(encryptedIVAndText)
        }

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

        private fun bytesToHex(hashInBytes: ByteArray): String {
            val result = CharArray(hashInBytes.size * 2)
            for (i in hashInBytes.indices) {
                val v = hashInBytes[i].toInt() and 0xFF
                result[i * 2] = HEX_CHARS[v ushr 4]
                result[i * 2 + 1] = HEX_CHARS[v and 0x0F]
            }
            return String(result)
        }

        fun decrypt(encrypted: String?, key: String?, initVector: String?): String? {
            try {
                if (encrypted == null || key == null || initVector == null) {
                    return null
                }
                val ivBytes = hexStringToByteArray(initVector)
                val iv = IvParameterSpec(ivBytes)
                val skeySpec = SecretKeySpec(hexStringToByteArray(key), "AES")

                val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
                cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv)
                val encryptedBytes = hexStringToByteArray(encrypted)
                // Invariant: New-format encrypted data prepends the IV to the ciphertext.
                // We check if the payload starts with the provided IV to decide whether to strip it.
                // This maintains backward compatibility with legacy data containing only the ciphertext.
                val hasIvPrefix = startsWith(encryptedBytes, ivBytes)
                val original = if (hasIvPrefix) {
                    cipher.doFinal(encryptedBytes, ivBytes.size, encryptedBytes.size - ivBytes.size)
                } else {
                    cipher.doFinal(encryptedBytes)
                }
                return String(original)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return null
        }

        private fun startsWith(array: ByteArray, prefix: ByteArray): Boolean {
            if (array.size < prefix.size) return false
            for (i in prefix.indices) {
                if (array[i] != prefix[i]) return false
            }
            return true
        }

        fun androidDecrypter(usrId: String?, usrRawPwd: String?, dbPwdKeyValue: String?, dbSalt: String?): Boolean {
            try {
                if (dbPwdKeyValue == null) return false
                val p = PBKDF2Parameters("HmacSHA1", "utf-8", dbSalt?.toByteArray(), 10)
                val dk = PBKDF2Engine(p).deriveKey(usrRawPwd, 20)
                val expected = try {
                    hexStringToByteArray(dbPwdKeyValue)
                } catch (e: Exception) {
                    return false
                }
                return MessageDigest.isEqual(dk, expected)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return false
        }

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
