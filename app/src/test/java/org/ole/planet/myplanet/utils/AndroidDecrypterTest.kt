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

        // The expected length of AES/CBC/PKCS5Padding encrypted "Hello, World!" (13 bytes) is 16 bytes.
        // In hex, that's 32 characters. The IV (16 bytes) is prepended, making it 64 hex chars.
        assertEquals(64, encrypted.length)

        // Let's verify decryption works with the generated encrypted text
        val decrypted = AndroidDecrypter.decrypt(encrypted, key, iv)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun testEncryptWithNullKeyAndIv() {
        val plainText = "Null key and IV"

        // DANGEROUS SECURITY PROPERTY DOCUMENTATION:
        // When key and IV are null, encrypt() silently pads keyBytes/ivBytes to all-zero arrays,
        // producing a deterministic ciphertext without failing.
        // This should arguably be disallowed, but we document the current behavior here.
        val encrypted = AndroidDecrypter.encrypt(plainText, null, null)
        assertNotNull(encrypted)

        // Decrypt() returns null if the passed key is null, so we must explicitly pass
        // the equivalent all-zero hex strings to round-trip this.
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

    @Test(expected = StringIndexOutOfBoundsException::class)
    fun testEncryptInvalidHexFormat() {
        val plainText = "Test"
        // "invalid_hex" has 11 chars (odd length), causing StringIndexOutOfBoundsException in hexStringToByteArray
        val key = "invalid_hex"
        val iv = "abcdef0123456789abcdef0123456789"
        AndroidDecrypter.encrypt(plainText, key, iv)
    }

    @Test
    fun testEncryptKeyTooLarge() {
        val plainText = "Test"
        // Key larger than 32 bytes (64 hex chars).
        // Since arraycopy copies from src to dest using `dest.size` (which is 32),
        // it doesn't actually throw an error if the source is larger! It just truncates it.
        // Let's document this behavior.
        val key = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef00"
        val iv = "abcdef0123456789abcdef0123456789"
        val encrypted = AndroidDecrypter.encrypt(plainText, key, iv)

        // Ensure that the encryption actually succeeds and truncates the larger key
        assertNotNull(encrypted)

        // The original key truncated to 64 characters
        val truncatedKey = key.substring(0, 64)
        val decrypted = AndroidDecrypter.decrypt(encrypted, truncatedKey, iv)
        assertEquals(plainText, decrypted)
    }
}
