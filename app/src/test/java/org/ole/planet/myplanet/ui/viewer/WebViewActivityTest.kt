package org.ole.planet.myplanet.ui.viewer

import android.app.Application
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WebViewActivityTest {

    private lateinit var tempDir: File
    private lateinit var resourceDir: File

    @Before
    fun setUp() {
        tempDir = createTempDirectory("test_ole").toFile()
        resourceDir = File(tempDir, "ole/res123").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `isWithinDirectory allows files inside resource directory`() {
        val validFile = File(resourceDir, "index.html")
        val validNestedFile = File(resourceDir, "sub/image.png")

        assertTrue(WebViewActivity.isWithinDirectory(validFile, resourceDir))
        assertTrue(WebViewActivity.isWithinDirectory(validNestedFile, resourceDir))
    }

    @Test
    fun `isWithinDirectory rejects path traversal escaping resource directory`() {
        val traversalFile = File(resourceDir, "../secret.txt")
        val nestedTraversalFile = File(resourceDir, "sub/../../etc/passwd")

        assertFalse(WebViewActivity.isWithinDirectory(traversalFile, resourceDir))
        assertFalse(WebViewActivity.isWithinDirectory(nestedTraversalFile, resourceDir))
    }

    @Test
    fun `encodedEntryPath defaults to index html when no entry is given`() {
        File(resourceDir, "index.html").writeText("<html/>")

        assertEquals("index.html", WebViewActivity.encodedEntryPath(resourceDir, null))
    }

    @Test
    fun `encodedEntryPath encodes each segment of a nested entry`() {
        File(resourceDir, "my pages").mkdirs()
        File(resourceDir, "my pages/start #1.html").writeText("<html/>")

        assertEquals("my%20pages/start%20%231.html", WebViewActivity.encodedEntryPath(resourceDir, "my pages/start #1.html"))
    }

    @Test
    fun `encodedEntryPath is null when the entry file is missing`() {
        assertNull(WebViewActivity.encodedEntryPath(resourceDir, "index.html"))
    }

    @Test
    fun `encodedEntryPath is null for an entry escaping the resource directory`() {
        File(tempDir, "ole/secret.html").writeText("<html/>")

        assertNull(WebViewActivity.encodedEntryPath(resourceDir, "../secret.html"))
    }
}
