package org.ole.planet.myplanet.utils

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `getCsvPreview returns parsed rows up to the row limit`() = runTest {
        val file = tempFolder.newFile("test.csv")
        file.writeText((1..8).joinToString("\n") { "A$it,B$it,C$it" })

        val preview = previewLoader.getCsvPreview(file)

        assertNotNull(preview)
        assertEquals(ResourcesPreviewLoader.CARD_PREVIEW_ROWS, preview!!.rows.size)
        assertEquals(listOf("A1", "B1", "C1"), preview.rows.first())
        assertEquals(3, preview.columnCount)
        assertTrue(preview.hasMoreRows)
    }

    @Test
    fun `getCsvPreview honours a custom row limit and reports no more rows`() = runTest {
        val file = tempFolder.newFile("small.csv")
        file.writeText((1..3).joinToString("\n") { "A$it,B$it" })

        val preview = previewLoader.getCsvPreview(file, maxRows = 5)

        assertNotNull(preview)
        assertEquals(3, preview!!.rows.size)
        assertFalse(preview.hasMoreRows)
    }

    @Test
    fun `getCsvPreview keeps quoted cells intact, trims cells and skips blank rows`() = runTest {
        val file = tempFolder.newFile("quoted.csv")
        file.writeText("Title, Link\n\n\"Reto de noviembre, 2025\", https://example.org\n")

        val preview = previewLoader.getCsvPreview(file)

        assertNotNull(preview)
        assertEquals(2, preview!!.rows.size)
        assertEquals(listOf("Title", "Link"), preview.rows[0])
        assertEquals(listOf("Reto de noviembre, 2025", "https://example.org"), preview.rows[1])
    }

    @Test
    fun `getCsvPreview reports the widest row as the column count`() = runTest {
        val file = tempFolder.newFile("ragged.csv")
        file.writeText("A,B\nC,D,E\n")

        val preview = previewLoader.getCsvPreview(file)

        assertNotNull(preview)
        assertEquals(3, preview!!.columnCount)
    }

    @Test
    fun `getCsvPreview returns null for invalid file`() = runTest {
        val file = File(tempFolder.root, "does_not_exist.csv")
        val preview = previewLoader.getCsvPreview(file)
        assertNull(preview)
    }

    @Test
    fun `getCsvPreview returns null for empty file`() = runTest {
        val file = tempFolder.newFile("empty.csv")
        val preview = previewLoader.getCsvPreview(file)
        assertNull(preview)
    }
}
