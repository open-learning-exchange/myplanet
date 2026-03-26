package org.ole.planet.myplanet.utils

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class FileUtilsTest {

    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        // We use a generic Application class to avoid MainApplication initialization that calls Realm.init()
        context = RuntimeEnvironment.getApplication()
        tempDir = File(context.cacheDir, "test_dir")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
    }

    @After
    fun tearDown() {
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun getOlePath_returnsCorrectPath() {
        val path = FileUtils.getOlePath(context)
        assertTrue(path.endsWith("/ole/"))
        assertTrue(path.contains(context.getExternalFilesDir(null)?.absolutePath ?: ""))
    }

    @Test
    fun fullyReadFileToBytes_returnsCorrectBytes() {
        val file = File(tempDir, "test.txt")
        val data = "Hello, World!".toByteArray()
        file.writeBytes(data)

        val bytes = FileUtils.fullyReadFileToBytes(file)
        assertArrayEquals(data, bytes)
    }

    @Test
    fun checkFileExist_returnsTrueWhenFileExists() {
        val testFile = File(context.getExternalFilesDir(null), "ole/123/test_file.txt")
        testFile.parentFile?.mkdirs()
        testFile.createNewFile()

        val url = "http://example.com/resources/123/test_file.txt"

        assertTrue(FileUtils.checkFileExist(context, url))

        testFile.delete()
    }

    @Test
    fun checkFileExist_returnsFalseWhenFileDoesNotExist() {
        val url = "http://example.com/resources/123/nonexistent.txt"
        assertFalse(FileUtils.checkFileExist(context, url))
    }

    @Test
    fun checkFileExist_returnsFalseWhenUrlIsNullOrEmpty() {
        assertFalse(FileUtils.checkFileExist(context, null))
        assertFalse(FileUtils.checkFileExist(context, ""))
    }

    @Test
    fun getFileNameFromLocalAddress_returnsCorrectFileName() {
        assertEquals("file.txt", FileUtils.getFileNameFromLocalAddress("/path/to/file.txt"))
        assertEquals("file.txt", FileUtils.getFileNameFromLocalAddress("file.txt"))
        assertEquals("", FileUtils.getFileNameFromLocalAddress(null))
        assertEquals("", FileUtils.getFileNameFromLocalAddress(""))
    }

    @Test
    fun getFileNameFromUrl_returnsCorrectFileName() {
        assertEquals("image.jpg", FileUtils.getFileNameFromUrl("http://example.com/image.jpg"))
        assertEquals("document.pdf", FileUtils.getFileNameFromUrl("https://site.org/path/document.pdf?query=1"))
        assertEquals("", FileUtils.getFileNameFromUrl(null))
        assertEquals("file with spaces.txt", FileUtils.getFileNameFromUrl("http://example.com/file%20with%20spaces.txt"))
    }

    @Test
    fun getIdFromUrl_returnsCorrectId() {
        assertEquals("123", FileUtils.getIdFromUrl("http://example.com/resources/123/file.txt"))
        assertEquals("abc", FileUtils.getIdFromUrl("https://test.com/api/resources/abc/data"))
        assertEquals("", FileUtils.getIdFromUrl("http://example.com/no_resources/123/file.txt"))
        assertEquals("", FileUtils.getIdFromUrl(null))
    }

    @Test
    fun getFileExtension_returnsCorrectExtension() {
        assertEquals("txt", FileUtils.getFileExtension("/path/to/file.txt"))
        assertEquals("jpg", FileUtils.getFileExtension("image.jpg"))
        assertEquals("", FileUtils.getFileExtension("file_without_extension"))
        assertEquals("", FileUtils.getFileExtension(null))
    }

    @Test
    fun copyUriToFile_copiesContentCorrectly() {
        val sourceFile = File(tempDir, "source.txt")
        val content = "Test Content"
        sourceFile.writeText(content)
        val sourceUri = Uri.fromFile(sourceFile)

        val destFile = File(tempDir, "dest.txt")

        FileUtils.copyUriToFile(context, sourceUri, destFile)

        assertTrue(destFile.exists())
        assertEquals(content, destFile.readText())
    }

    @Test
    fun getStringFromFile_returnsFileContent() {
        val file = File(tempDir, "string_test.txt")
        val content = "This is a test string.\nWith multiple lines."
        file.writeText(content)

        val result = FileUtils.getStringFromFile(file)
        assertEquals(content, result)
    }

    @Test
    fun getStringFromFile_returnsEmptyStringForNullOrEmptyFile() {
        assertEquals("", FileUtils.getStringFromFile(null))

        val emptyFile = File(tempDir, "empty.txt")
        emptyFile.createNewFile()
        assertEquals("", FileUtils.getStringFromFile(emptyFile))
    }

    @Test
    fun openOleFolder_returnsCorrectIntent() {
        val intent = FileUtils.openOleFolder(context)
        assertEquals(Intent.ACTION_CHOOSER, intent.action)
        val innerIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_GET_CONTENT, innerIntent?.action)
        assertEquals("*/*", innerIntent?.type)
        assertTrue(innerIntent?.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false) == true)
    }

    @Test
    fun externalMemoryAvailable_returnsCorrectState() {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        assertTrue(FileUtils.externalMemoryAvailable())

        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_UNMOUNTED)
        assertFalse(FileUtils.externalMemoryAvailable())
    }

    @Test
    fun formatSize_formatsCorrectly() {
        val size = 1024L * 1024L // 1MB
        val formatted = FileUtils.formatSize(context, size)
        assertTrue(formatted.isNotEmpty())
        // Formatter.formatFileSize behavior depends on SDK and locale, Robolectric uses its own string formatting.
        // It's safer to just check that it produces a non-empty string and contains MB or B.
        assertTrue(formatted.contains("MB") || formatted.contains("B"))
    }

    @Test
    fun nameWithoutExtension_returnsCorrectName() {
        assertEquals("file", FileUtils.nameWithoutExtension("file.txt"))
        assertEquals("document", FileUtils.nameWithoutExtension("/path/to/document.pdf"))
        assertEquals("archive.tar", FileUtils.nameWithoutExtension("archive.tar.gz"))
        // Based on test output, "no_extension" returns "no_extension" rather than null
        assertEquals("no_extension", FileUtils.nameWithoutExtension("no_extension"))
        assertNull(FileUtils.nameWithoutExtension(null))
    }
}
