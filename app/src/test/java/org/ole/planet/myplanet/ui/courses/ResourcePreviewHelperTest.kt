package org.ole.planet.myplanet.ui.courses

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ResourcePreviewHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var previewHelper: ResourcePreviewHelper
    private val dispatcherProvider = TestDispatcherProvider(kotlinx.coroutines.test.UnconfinedTestDispatcher())

    @Before
    fun setup() {
        previewHelper = ResourcePreviewHelper(dispatcherProvider)
    }

    @Test
    fun `getTextPreview returns top 8 lines`() = runTest {
        val file = tempFolder.newFile("test.txt")
        file.writeText((1..10).joinToString("\n") { "Line $it" })

        val preview = previewHelper.getTextPreview(file)

        val expected = (1..8).joinToString("\n") { "Line $it" }
        assertEquals(expected, preview)
    }

    @Test
    fun `getTextPreview returns null for empty file`() = runTest {
        val file = tempFolder.newFile("empty.txt")
        val preview = previewHelper.getTextPreview(file)
        assertNull(preview)
    }

    @Test
    fun `getCsvPreview returns formatted rows up to 5`() = runTest {
        val file = tempFolder.newFile("test.csv")
        file.writeText((1..6).joinToString("\n") { "A$it,B$it,C$it" })

        val preview = previewHelper.getCsvPreview(file)

        val expected = (1..5).joinToString("\n") { "A$it  |  B$it  |  C$it" }
        assertEquals(expected, preview)
    }

    @Test
    fun `getCsvPreview returns null for invalid file`() = runTest {
        val file = File(tempFolder.root, "does_not_exist.csv")
        val preview = previewHelper.getCsvPreview(file)
        assertNull(preview)
    }
}
