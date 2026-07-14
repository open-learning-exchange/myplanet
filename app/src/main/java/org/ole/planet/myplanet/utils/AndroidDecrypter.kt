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
        @Throws(Exception::class)
        fun encrypt(plainText: String, key: String?, iv: String?): String {
            val clean = plainText.toByteArray()
            val ivSize = 16
            val ivBytes = ByteArray(ivSize)
            if (iv != null) {
                val decodedIv = hexStringToByteArray(iv)
                System.arraycopy(decodedIv, 0, ivBytes, 0, minOf(decodedIv.size, ivBytes.size))
            } else {
                SecureRandom().nextBytes(ivBytes)
            }
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
            val sb = StringBuilder()
            for (b in hashInBytes) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        }

        fun decrypt(encrypted: String?, key: String?, initVector: String?): String? {
            try {
                if (encrypted == null || key == null) {
                    return null
                }
                val encryptedBytes = hexStringToByteArray(encrypted)
                val ivBytes: ByteArray
                val actualEncryptedBytes: ByteArray

                if (initVector == null) {
                    if (encryptedBytes.size < 16) return null
                    ivBytes = encryptedBytes.sliceArray(0 until 16)
                    actualEncryptedBytes = encryptedBytes.sliceArray(16 until encryptedBytes.size)
                } else {
                    val providedIvBytes = hexStringToByteArray(initVector)
                    if (encryptedBytes.size >= providedIvBytes.size && providedIvBytes.contentEquals(encryptedBytes.sliceArray(0 until providedIvBytes.size))) {
                        ivBytes = providedIvBytes
                        actualEncryptedBytes = encryptedBytes.sliceArray(providedIvBytes.size until encryptedBytes.size)
                    } else {
                        ivBytes = providedIvBytes
                        actualEncryptedBytes = encryptedBytes
                    }
                }

                val iv = IvParameterSpec(ivBytes)
                val skeySpec = SecretKeySpec(hexStringToByteArray(key), "AES")

                val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
                cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv)
                val original = cipher.doFinal(actualEncryptedBytes)
                return String(original)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return null
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
