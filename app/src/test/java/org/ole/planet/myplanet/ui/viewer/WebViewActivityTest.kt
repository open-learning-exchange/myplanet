package org.ole.planet.myplanet.ui.viewer

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
}
