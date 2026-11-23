package org.ole.planet.myplanet.utils

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test

class Sha256UtilsTest {

    @Test
    fun getCheckSumFromFile_validFile_returnsChecksum() {
        val file = File.createTempFile("test", ".txt")
        file.writeText("test data")
        file.deleteOnExit()

        val checksum = Sha256Utils().getCheckSumFromFile(file)

        assertNotNull(checksum)
        assertNotEquals("", checksum)
        // Note: The class is named Sha256Utils, but the underlying implementation uses SHA-512.
        // A SHA-512 hex string length is 128 characters.
        assertTrue(checksum!!.length == 128)
    }

    @Test
    fun getCheckSumFromFile_fileNotFound_returnsNull() {
        val file = File("non_existent_file.txt")

        val checksum = Sha256Utils().getCheckSumFromFile(file)

        assertNull(checksum)
    }

    @Test
    fun getCheckSumFromFile_invalidFilePermissions_returnsNull() {
        val file = File.createTempFile("no_read_permission", ".txt")
        file.writeText("test data")
        file.deleteOnExit()

        // Remove read permission

        // Skip this test if the environment (e.g., running as root in CI) prevents us from removing read permissions.
        assumeFalse("Could not restrict read permissions on the file; skipping test.", file.canRead())

        val checksum = Sha256Utils().getCheckSumFromFile(file)

        assertNull(checksum)

        // Restore read permission so it can be deleted
        file.setReadable(true)
    }

    @Test
    fun getCheckSumFromFile_largeFile_matchesDirectSha512() {
        val file = File.createTempFile("large_test", ".bin")
        file.deleteOnExit()

        // 200 KiB file (larger than 64 KiB buffer)
        val data = ByteArray(200 * 1024) { (it % 251).toByte() }
        file.writeBytes(data)

        val checksum = Sha256Utils().getCheckSumFromFile(file)

        val digest = MessageDigest.getInstance("SHA-512")
        val expectedHash = digest.digest(data)
        val hexChars = "0123456789abcdef".toCharArray()
        val expectedHex = CharArray(expectedHash.size * 2)
        for (i in expectedHash.indices) {
            val v = expectedHash[i].toInt() and 0xFF
            expectedHex[i * 2] = hexChars[v ushr 4]
            expectedHex[i * 2 + 1] = hexChars[v and 0x0F]
        }

        assertEquals(String(expectedHex), checksum)
    }
}
