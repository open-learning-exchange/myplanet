package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AndroidDecrypterTest {

    @Test
    fun testEncryptValidInput() {
        val plainText = "Hello, World!"
        // AES-256 requires 32 bytes key. In hex, 64 characters.
        val key = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        // IV requires 16 bytes. In hex, 32 characters.
        val iv = "abcdef0123456789abcdef0123456789"

        val encrypted = AndroidDecrypter.encrypt(plainText, key, iv)
        assertNotNull(encrypted)

        // Let's verify decryption works with the generated encrypted text
        val decrypted = AndroidDecrypter.decrypt(encrypted, key, iv)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun testEncryptWithNullKeyAndIv() {
        val plainText = "Null key and IV"
        // When key and IV are null, they default to arrays of zeros
        val encrypted = AndroidDecrypter.encrypt(plainText, null, null)
        assertNotNull(encrypted)

        // Decrypt using zeroed key and IV
        val zeroKey = "00".repeat(32)
        val zeroIv = "00".repeat(16)
        val decrypted = AndroidDecrypter.decrypt(encrypted, zeroKey, zeroIv)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun testEncryptEmptyString() {
        val plainText = ""
        val key = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val iv = "abcdef0123456789abcdef0123456789"

        val encrypted = AndroidDecrypter.encrypt(plainText, key, iv)
        assertNotNull(encrypted)

        val decrypted = AndroidDecrypter.decrypt(encrypted, key, iv)
        assertEquals(plainText, decrypted)
    }

    @Test(expected = Exception::class)
    fun testEncryptInvalidKeyLength() {
        val plainText = "Test"
        val key = "invalid_hex"
        val iv = "abcdef0123456789abcdef0123456789"
        // Should throw Exception due to invalid hex or key size
        AndroidDecrypter.encrypt(plainText, key, iv)
    }
}
