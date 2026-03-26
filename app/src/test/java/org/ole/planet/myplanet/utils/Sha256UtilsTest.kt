package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.File

class Sha256UtilsTest {

    @Test
    fun getCheckSumFromFile_validFile_returnsChecksum() {
        val file = File.createTempFile("test", ".txt")
        file.writeText("test data")
        file.deleteOnExit()

        val checksum = Sha256Utils().getCheckSumFromFile(file)

        assertNotEquals("", checksum)
        // Note: The class is named Sha256Utils, but the underlying implementation uses SHA-512.
        // A SHA-512 hex string length is 128 characters.
        assertTrue(checksum.length == 128)
    }

    @Test
    fun getCheckSumFromFile_fileNotFound_returnsEmptyString() {
        val file = File("non_existent_file.txt")

        val checksum = Sha256Utils().getCheckSumFromFile(file)

        assertEquals("", checksum)
    }

    @Test
    fun getCheckSumFromFile_invalidFilePermissions_returnsEmptyString() {
        val file = File.createTempFile("no_read_permission", ".txt")
        file.writeText("test data")
        file.deleteOnExit()

        // Remove read permission
        val success = file.setReadable(false)

        // Skip this test if the environment (e.g., running as root in CI) prevents us from removing read permissions.
        assumeFalse("Could not restrict read permissions on the file; skipping test.", file.canRead())

        val checksum = Sha256Utils().getCheckSumFromFile(file)

        assertEquals("", checksum)

        // Restore read permission so it can be deleted
        file.setReadable(true)
    }
}
