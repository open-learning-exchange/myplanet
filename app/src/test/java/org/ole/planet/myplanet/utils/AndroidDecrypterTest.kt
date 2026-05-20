package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun testDecryptLegacyFormat() {
        val plainText = "Legacy format test"
        val key = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val iv = "abcdef0123456789abcdef0123456789"

        val encryptedWithIv = AndroidDecrypter.encrypt(plainText, key, iv)
        // Remove the prepended IV (32 hex chars for 16 bytes) to simulate legacy format
        val legacyEncrypted = encryptedWithIv.substring(32)

        val decrypted = AndroidDecrypter.decrypt(legacyEncrypted, key, iv)
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

    @Test
    fun testAndroidDecrypter() {
        val userId = "testUser"
        val password = "password123"
        val salt = "someSalt"

        // Use the same logic as in the function to get the expected hash
        val p = de.rtner.security.auth.spi.PBKDF2Parameters("HmacSHA1", "utf-8", salt.toByteArray(), 10)
        val dk = de.rtner.security.auth.spi.PBKDF2Engine(p).deriveKey(password, 20)
        val expectedHash = de.rtner.misc.BinTools.bin2hex(dk).lowercase(java.util.Locale.ROOT)

        val result = AndroidDecrypter.androidDecrypter(userId, password, expectedHash, salt)
        assertTrue(result)
    }
}
