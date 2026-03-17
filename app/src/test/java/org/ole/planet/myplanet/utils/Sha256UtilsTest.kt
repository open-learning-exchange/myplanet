package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class Sha256UtilsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val sha256Utils = Sha256Utils()

    @Test
    fun testGetCheckSumFromFile_emptyFile() {
        val file = tempFolder.newFile("empty.txt")
        // SHA-512 for empty string
        val expected = "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e"
        val actual = sha256Utils.getCheckSumFromFile(file)
        assertEquals(expected, actual)
    }

    @Test
    fun testGetCheckSumFromFile_knownContent() {
        val file = tempFolder.newFile("hello.txt")
        FileOutputStream(file).use { it.write("Hello World".toByteArray()) }
        // SHA-512 for "Hello World"
        val expected = "2c74fd17edafd3902171081d96458430ea13b0f936c96a2b371113802e17d77c1544a086326174154483756811204d169b14f828775f0a0584408103c800c929"
        val actual = sha256Utils.getCheckSumFromFile(file)
        assertEquals(expected, actual)
    }

    @Test
    fun testGetCheckSumFromFile_nonExistentFile() {
        val file = File(tempFolder.root, "non_existent.txt")
        val actual = sha256Utils.getCheckSumFromFile(file)
        assertEquals("", actual)
    }
}
