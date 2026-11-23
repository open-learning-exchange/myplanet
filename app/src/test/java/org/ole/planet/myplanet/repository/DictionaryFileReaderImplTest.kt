package org.ole.planet.myplanet.repository

import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.StoragePathResolver

class DictionaryFileReaderImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storagePathResolver: StoragePathResolver
    private lateinit var dictionaryFileReader: DictionaryFileReaderImpl

    @Before
    fun setup() {
        storagePathResolver = mockk()
        dictionaryFileReader = DictionaryFileReaderImpl(storagePathResolver)
    }

    @Test
    fun `exists returns false when resolver reports non-existent file`() {
        val nonExistentFile = File(tempFolder.root, "non_existent.json")
        every { storagePathResolver.resolveFileFromUrl(Constants.DICTIONARY_URL) } returns nonExistentFile

        assertFalse(dictionaryFileReader.exists())
    }

    @Test
    fun `exists returns false when resolver reports empty file`() {
        val emptyFile = tempFolder.newFile("empty_dictionary.json")
        every { storagePathResolver.resolveFileFromUrl(Constants.DICTIONARY_URL) } returns emptyFile

        assertFalse(dictionaryFileReader.exists())
    }

    @Test
    fun `exists returns true when resolver reports existing non-empty file`() {
        val dictionaryFile = tempFolder.newFile("dictionary.json")
        dictionaryFile.writeText("[{\"word\": \"test\"}]")
        every { storagePathResolver.resolveFileFromUrl(Constants.DICTIONARY_URL) } returns dictionaryFile

        assertTrue(dictionaryFileReader.exists())
    }

    @Test
    fun `readText returns null when resolver reports non-existent file`() {
        val nonExistentFile = File(tempFolder.root, "non_existent.json")
        every { storagePathResolver.resolveFileFromUrl(Constants.DICTIONARY_URL) } returns nonExistentFile

        assertNull(dictionaryFileReader.readText())
    }

    @Test
    fun `readText returns text content when resolver reports existing file`() {
        val content = "[{\"word\": \"hello\"}]"
        val dictionaryFile = tempFolder.newFile("dictionary.json")
        dictionaryFile.writeText(content)
        every { storagePathResolver.resolveFileFromUrl(Constants.DICTIONARY_URL) } returns dictionaryFile

        assertEquals(content, dictionaryFileReader.readText())
    }
}
