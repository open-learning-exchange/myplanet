package org.ole.planet.myplanet.utils

import android.media.MediaMetadataRetriever
import io.mockk.every
import io.mockk.just
import io.mockk.mockkConstructor
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ResourcesPreviewLoaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var previewLoader: ResourcesPreviewLoader
    private val dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher())

    @Before
    fun setup() {
        previewLoader = ResourcesPreviewLoader(dispatcherProvider)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getTextPreview returns top 8 lines`() = runTest {
        val file = tempFolder.newFile("test.txt")
        file.writeText((1..10).joinToString("\n") { "Line $it" })

        val preview = previewLoader.getTextPreview(file)

        val expected = (1..8).joinToString("\n") { "Line $it" }
        assertEquals(expected, preview)
    }

    @Test
    fun `getTextPreview returns null for empty file`() = runTest {
        val file = tempFolder.newFile("empty.txt")
        val preview = previewLoader.getTextPreview(file)
        assertNull(preview)
    }

    @Test
    fun `getCsvPreview returns formatted rows up to 5`() = runTest {
        val file = tempFolder.newFile("test.csv")
        file.writeText((1..6).joinToString("\n") { "A$it,B$it,C$it" })

        val preview = previewLoader.getCsvPreview(file)

        val expected = (1..5).joinToString("\n") { "A$it  |  B$it  |  C$it" }
        assertEquals(expected, preview)
    }

    @Test
    fun `getCsvPreview returns null for invalid file`() = runTest {
        val file = File(tempFolder.root, "does_not_exist.csv")
        val preview = previewLoader.getCsvPreview(file)
        assertNull(preview)
    }

    @Test
    fun `two calls for an unchanged file return cached preview without re-reading`() = runTest {
        val file = tempFolder.newFile("cache_test.txt")
        file.writeText("Original content line")
        val initialLastModified = file.lastModified()

        val firstCall = previewLoader.getTextPreview(file)
        assertEquals("Original content line", firstCall)

        file.writeText("Modified content line")
        file.setLastModified(initialLastModified)

        val secondCall = previewLoader.getTextPreview(file)
        assertEquals("Original content line", secondCall)
    }

    @Test
    fun `file whose lastModified changes is re-read`() = runTest {
        val file = tempFolder.newFile("mod_test.txt")
        file.writeText("Original content line")
        val initialLastModified = file.lastModified()

        val firstCall = previewLoader.getTextPreview(file)
        assertEquals("Original content line", firstCall)

        file.writeText("Updated content line")
        file.setLastModified(initialLastModified + 2000L)

        val secondCall = previewLoader.getTextPreview(file)
        assertEquals("Updated content line", secondCall)
    }

    @Test
    fun `failing call is not cached`() = runTest {
        val file = File(tempFolder.root, "non_existent.txt")

        val firstCall = previewLoader.getTextPreview(file)
        assertNull(firstCall)

        file.writeText("Now file exists")

        val secondCall = previewLoader.getTextPreview(file)
        assertEquals("Now file exists", secondCall)
    }

    @Test
    fun `cache evicts past its cap`() = runTest {
        val firstFile = tempFolder.newFile("file_0.txt")
        firstFile.writeText("First file content")
        val firstLastModified = firstFile.lastModified()

        assertEquals("First file content", previewLoader.getTextPreview(firstFile))

        for (i in 1..ResourcesPreviewLoader.MAX_CACHE_SIZE + 5) {
            val file = tempFolder.newFile("file_$i.txt")
            file.writeText("Content $i")
            previewLoader.getTextPreview(file)
        }

        // Write new text with identical length (18 chars) and same lastModified so CacheKey remains identical.
        // If eviction broke, getTextPreview would return the old cached value ("First file content").
        firstFile.writeText("Overwritten text!!")
        firstFile.setLastModified(firstLastModified)

        val result = previewLoader.getTextPreview(firstFile)
        assertEquals("Overwritten text!!", result)
    }

    @Test
    fun `getAudioPreview returns empty string for invalid audio file and is not cached`() = runTest {
        val file = tempFolder.newFile("fake.mp3")
        file.writeText("Not audio content")
        mockkConstructor(MediaMetadataRetriever::class)
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(any<String>()) } throws IllegalArgumentException("not audio")
        every { anyConstructed<MediaMetadataRetriever>().release() } just runs

        val firstCall = previewLoader.getAudioPreview(file)
        assertEquals("", firstCall)

        val secondCall = previewLoader.getAudioPreview(file)
        assertEquals("", secondCall)
        verify(exactly = 2) { anyConstructed<MediaMetadataRetriever>().setDataSource(file.absolutePath) }
    }
}
