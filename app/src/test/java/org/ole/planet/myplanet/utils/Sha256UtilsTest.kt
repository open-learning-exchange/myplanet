package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class Sha256UtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var sha256Utils: Sha256Utils

    @Before
    fun setUp() {
        sha256Utils = Sha256Utils()
    }

    @Test
    fun testGetCheckSumFromFile_EmptyFile() {
        val file = tempFolder.newFile("empty.txt")
        // SHA-512 of an empty file
        val expected = "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e"
        val actual = sha256Utils.getCheckSumFromFile(file)
        assertEquals(expected, actual)
    }

    @Test
    fun testGetCheckSumFromFile_HelloWorld() {
        val file = tempFolder.newFile("hello.txt")
        FileOutputStream(file).use { it.write("Hello World".toByteArray()) }
        // SHA-512 of "Hello World"
        val expected = "2c74fd17edafd80e8447b0d46741ee243b7eb74dd2149a0ab1b9246fb30382f27e853d8585719e0e67cbda0daa8f51671064615d645ae27acb15bfb1447f459b"
        val actual = sha256Utils.getCheckSumFromFile(file)
        assertEquals(expected, actual)
    }

    @Test
    fun testGetCheckSumFromFile_NonExistentFile() {
        val file = File(tempFolder.root, "non_existent.txt")
        val actual = sha256Utils.getCheckSumFromFile(file)
        assertEquals("", actual)
    }
}
