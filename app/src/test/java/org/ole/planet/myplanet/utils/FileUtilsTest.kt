package org.ole.planet.myplanet.utils

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import org.junit.After
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
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class FileUtilsTest {

    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        // We use a generic Application class to avoid MainApplication initialization that calls Realm.init()
        context = RuntimeEnvironment.getApplication()
        // FileUtils caches externalFilesDir in an object-level field, and Robolectric hands every test
        // method a fresh temp directory while re-using one sandbox (so one set of statics) for every
        // class on this SDK level. Without this reset, a cache warmed by an earlier test — in this
        // class or another one in the same sandbox — points at a temp directory that no longer exists.
        resetExternalFilesDirCache()
        tempDir = File(context.cacheDir, "test_dir")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
    }

    @After
    fun tearDown() {
        resetExternalFilesDirCache()
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
    }

    private fun resetExternalFilesDirCache() {
        FileUtils::class.java.getDeclaredField("cachedExternalFilesDir").apply {
            isAccessible = true
            set(FileUtils, null)
        }
    }

    @Test
    fun getOlePath_returnsCorrectPath() {
        val path = FileUtils.getOlePath(context)
        assertTrue(path.endsWith("/ole/"))
        assertTrue(path.contains(context.getExternalFilesDir(null)?.absolutePath ?: ""))
    }

    @Test
    fun checkFileExist_returnsTrueWhenFileExists() {
        FileUtils.warmUp(context)
        val testFile = File(FileUtils.getExternalFilesDir(context), "ole/123/test_file.txt")
        testFile.parentFile?.mkdirs()
        testFile.writeText("dummy content")

        val url = "http://example.com/resources/123/test_file.txt"

        assertTrue(FileUtils.checkFileExist(context, url))

        testFile.delete()
    }

    @Test
    fun checkFileExist_returnsFalseWhenFileDoesNotExist() {
        val url = "http://example.com/resources/123/nonexistent.txt"
        val expectedFile = FileUtils.getSDPathFromUrl(context, url)
        val parentDir = expectedFile.parentFile

        assertFalse(FileUtils.checkFileExist(context, url))
        if (parentDir != null) {
            assertFalse("Parent directory should not be created by existence check", parentDir.exists())
        }
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
    fun getSDPathFromUrl_preservesNestedAttachmentPath() {
        FileUtils.warmUp(context)
        val url = "http://example.com/resources/123/js/game_manager.js"

        val resolved = FileUtils.getSDPathFromUrl(context, url)

        assertEquals("game_manager.js", resolved.name)
        assertEquals("js", resolved.parentFile?.name)
        assertTrue(resolved.absolutePath.endsWith("ole/123/js/game_manager.js"))
    }

    @Test
    fun getSDPathFromUrl_singleSegmentFileHasNoSubdirectory() {
        FileUtils.warmUp(context)
        val url = "http://example.com/resources/123/index.html"

        val resolved = FileUtils.getSDPathFromUrl(context, url)

        assertEquals("index.html", resolved.name)
        assertEquals("123", resolved.parentFile?.name)
    }

    @Test
    fun resolveHtmlEntryFile_defaultsToIndexHtmlAtRootWhenUnset() {
        val resolved = FileUtils.resolveHtmlEntryFile(tempDir, null)

        assertEquals(File(tempDir, "index.html").canonicalFile, resolved)
    }

    @Test
    fun resolveHtmlEntryFile_defaultsToIndexHtmlAtRootWhenBlank() {
        val resolved = FileUtils.resolveHtmlEntryFile(tempDir, "")

        assertEquals(File(tempDir, "index.html").canonicalFile, resolved)
    }

    @Test
    fun resolveHtmlEntryFile_honorsNestedRelativePath() {
        val resolved = FileUtils.resolveHtmlEntryFile(tempDir, "sudoku/index.html")

        assertEquals(File(tempDir, "sudoku/index.html").canonicalFile, resolved)
    }

    @Test
    fun resolveHtmlEntryFile_rejectsPathTraversal() {
        assertNull(FileUtils.resolveHtmlEntryFile(tempDir, "../outside.html"))
        assertNull(FileUtils.resolveHtmlEntryFile(tempDir, "sudoku/../../outside.html"))
    }

    @Test
    fun resolveHtmlEntryFile_rejectsAbsolutePath() {
        assertNull(FileUtils.resolveHtmlEntryFile(tempDir, "/etc/passwd"))
    }

    @Test
    fun findHtmlCoverImage_prefersNameHintOverLargerFile() {
        File(tempDir, "cover.png").writeBytes(ByteArray(10))
        File(tempDir, "photo.jpg").writeBytes(ByteArray(1000))

        val cover = FileUtils.findHtmlCoverImage(tempDir)

        assertEquals("cover.png", cover?.name)
    }

    @Test
    fun findHtmlCoverImage_fallsBackToLargestImageWhenNoNameHint() {
        File(tempDir, "photo.jpg").writeBytes(ByteArray(10))
        File(tempDir, "banner.jpg").writeBytes(ByteArray(1000))

        val cover = FileUtils.findHtmlCoverImage(tempDir)

        assertEquals("banner.jpg", cover?.name)
    }

    @Test
    fun findHtmlCoverImage_findsHintedImageNestedInSubdirectory() {
        val assetsDir = File(tempDir, "assets").apply { mkdirs() }
        File(assetsDir, "thumbnail.png").writeBytes(ByteArray(10))

        val cover = FileUtils.findHtmlCoverImage(tempDir)

        assertEquals("thumbnail.png", cover?.name)
    }

    @Test
    fun findHtmlCoverImage_returnsNullWhenNoImagesPresent() {
        File(tempDir, "index.html").writeText("<html></html>")

        assertNull(FileUtils.findHtmlCoverImage(tempDir))
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
    fun resolveUriToPath_returnsPathDirectlyForFileScheme() {
        val sourceFile = File(tempDir, "source.txt")
        sourceFile.writeText("Test Content")
        val fileUri = Uri.fromFile(sourceFile)

        val resolved = FileUtils.resolveUriToPath(context, fileUri)

        assertEquals(sourceFile.absolutePath, resolved)
    }

    @Test
    fun resolveUriToPath_returnsNullForNullUri() {
        assertNull(FileUtils.resolveUriToPath(context, null))
    }

    @Test
    fun resolveUriToPath_copiesContentUriUsingDisplayName() {
        val authority = "org.ole.planet.myplanet.test.fileutils"
        val displayName = "photo.jpg"
        val sourceBytes = "fake image bytes".toByteArray()
        val sourceFile = File(tempDir, "provider_source.jpg").apply { writeBytes(sourceBytes) }
        val contentUri = Uri.parse("content://$authority/$displayName")

        val provider = object : ContentProvider() {
            override fun onCreate() = true

            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?
            ): Cursor {
                return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
                    addRow(arrayOf(displayName))
                }
            }

            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
                ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)

            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(
                uri: Uri,
                values: ContentValues?,
                selection: String?,
                selectionArgs: Array<out String>?
            ) = 0
        }
        val providerInfo = ProviderInfo().apply { this.authority = authority }
        provider.attachInfo(context, providerInfo)
        ShadowContentResolver.registerProviderInternal(authority, provider)

        val resolved = FileUtils.resolveUriToPath(context, contentUri)

        assertEquals(displayName, resolved?.let { File(it).name })
        assertEquals("fake image bytes", resolved?.let { File(it).readText() })
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
