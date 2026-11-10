package org.ole.planet.myplanet.utils

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PdfTextExtractorTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private lateinit var extractor: PdfTextExtractor

    @Before
    fun setUp() {
        mockkStatic(PDFBoxResourceLoader::class)
        every { PDFBoxResourceLoader.init(any()) } returns Unit
        extractor = PdfTextExtractor(context, TestDispatcherProvider(testDispatcher))
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `extractText initialises PDFBox itself before loading the file`() = runTest(testDispatcher) {
        extractor.extractText(File("/non_existent_directory/non_existent_file.pdf"))

        verify(exactly = 1) { PDFBoxResourceLoader.init(context) }
    }

    @Test
    fun `extractText returns empty string for a file that cannot be read`() = runTest(testDispatcher) {
        val result = extractor.extractText(File("/non_existent_directory/non_existent_file.pdf"))

        assertEquals("", result)
    }
}
