package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class Sha256UtilsTest {

    @Test
    fun getCheckSumFromFile_returnsEmptyStringOnException() {
        val nonExistentFile = File("path/that/does/not/exist.txt")
        val hash = Sha256Utils().getCheckSumFromFile(nonExistentFile)
        assertEquals("", hash)
    }
}
