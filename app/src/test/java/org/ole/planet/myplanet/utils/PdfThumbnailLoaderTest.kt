package org.ole.planet.myplanet.utils

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PdfThumbnailLoaderTest {

    @Before
    fun setUp() {
        PdfThumbnailLoader.evictAll()
    }

    @Test
    fun targetWidthPxZeroReturnsNull() = runTest {
        val file = File("some_file.pdf")
        val dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val result = PdfThumbnailLoader.firstPageBitmap(file, dispatcherProvider, 0)
        assertNull(result)
    }

    @Test
    fun missingOrNonPdfFileReturnsNullAndDoesNotThrow() = runTest {
        val missingFile = File("non_existent_file.pdf")
        val dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val result1 = PdfThumbnailLoader.firstPageBitmap(missingFile, dispatcherProvider, 100)
        assertNull(result1)

        val nonPdfFile = File.createTempFile("test_non_pdf", ".txt")
        nonPdfFile.writeText("This is not a pdf file")
        nonPdfFile.deleteOnExit()

        val result2 = PdfThumbnailLoader.firstPageBitmap(nonPdfFile, dispatcherProvider, 100)
        assertNull(result2)
    }

    @Test
    fun statCallsRunOnIo() = runTest {
        val isOnIo = ThreadLocal<Boolean>()
        val trackingIoDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                isOnIo.set(true)
                try {
                    block.run()
                } finally {
                    isOnIo.set(false)
                }
            }
        }

        val dispatcherProvider = mockk<DispatcherProvider>()
        every { dispatcherProvider.io } returns trackingIoDispatcher

        val tempFile = File.createTempFile("test_pdf", ".pdf")
        tempFile.deleteOnExit()

        var lastModifiedCalledOnIo = false
        var lengthCalledOnIo = false

        val spyFile = spyk(tempFile)
        every { spyFile.lastModified() } answers {
            if (isOnIo.get() == true) {
                lastModifiedCalledOnIo = true
            }
            callOriginal()
        }
        every { spyFile.length() } answers {
            if (isOnIo.get() == true) {
                lengthCalledOnIo = true
            }
            callOriginal()
        }

        val result = PdfThumbnailLoader.firstPageBitmap(spyFile, dispatcherProvider, 100)
        assertNull(result)
        assertTrue("lastModified should have been called on IO dispatcher", lastModifiedCalledOnIo)
        assertTrue("length should have been called on IO dispatcher", lengthCalledOnIo)
    }

    @Test
    fun thumbnailSavedOnDiskIsUsedWithoutOpeningThePdf() = runTest {
        val dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val cacheDir = kotlin.io.path.createTempDirectory("pdf_cache").toFile()
        
        val pdf = File.createTempFile("cached", ".pdf").apply { writeText("not a pdf"); deleteOnExit() }
        val key = "${pdf.absolutePath}_${pdf.lastModified()}_${pdf.length()}_100"
        val diskFile = PdfThumbnailLoader.diskFile(cacheDir, key)
        diskFile.parentFile!!.mkdirs()
        diskFile.outputStream().use { createBitmap(100, 140).compress(Bitmap.CompressFormat.JPEG, 85, it) }

        val result = PdfThumbnailLoader.firstPageBitmap(pdf, dispatcherProvider, 100, cacheDir)

        assertNotNull(result)
        cacheDir.deleteRecursively()
    }

    @Test
    fun diskCacheDropsLeastRecentlyUsedThumbnailsOverTheLimit() {
        val dir = kotlin.io.path.createTempDirectory("pdf_trim").toFile()
        val old = File(dir, "old.jpg").apply { writeBytes(ByteArray(600)); setLastModified(1_000) }
        val recent = File(dir, "recent.jpg").apply { writeBytes(ByteArray(600)); setLastModified(2_000) }

        PdfThumbnailLoader.trimDisk(dir, maxBytes = 1_000)

        assertFalse(old.exists())
        assertTrue(recent.exists())
        assertEquals(1, dir.listFiles()!!.size)
        dir.deleteRecursively()
    }
}
