package org.ole.planet.myplanet.ui.resources

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.services.DownloadService
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ResourcesCardBinderTest {

    private lateinit var context: Context

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testTypeColorResMapping() {
        assertEquals(R.color.type_pdf, ResourcesCardBinder.typeColorRes(ResourcesMediaType.PDF))
        assertEquals(R.color.type_video, ResourcesCardBinder.typeColorRes(ResourcesMediaType.VIDEO))
        assertEquals(R.color.type_audio, ResourcesCardBinder.typeColorRes(ResourcesMediaType.AUDIO))
        assertEquals(R.color.type_book, ResourcesCardBinder.typeColorRes(ResourcesMediaType.BOOK))
    }

    @Test
    fun testTypeIconResMapping() {
        assertEquals(R.drawable.ic_type_pdf, ResourcesCardBinder.typeIconRes(ResourcesMediaType.PDF))
        assertEquals(R.drawable.ic_type_video, ResourcesCardBinder.typeIconRes(ResourcesMediaType.VIDEO))
        assertEquals(R.drawable.ic_type_audio, ResourcesCardBinder.typeIconRes(ResourcesMediaType.AUDIO))
        assertEquals(R.drawable.ic_type_book, ResourcesCardBinder.typeIconRes(ResourcesMediaType.BOOK))
    }

    @Test
    fun testTypeLabelResMapping() {
        assertEquals(R.string.filter_pdfs, ResourcesCardBinder.typeLabelRes(ResourcesMediaType.PDF))
        assertEquals(R.string.filter_videos, ResourcesCardBinder.typeLabelRes(ResourcesMediaType.VIDEO))
        assertEquals(R.string.filter_audio, ResourcesCardBinder.typeLabelRes(ResourcesMediaType.AUDIO))
        assertEquals(R.string.filter_books, ResourcesCardBinder.typeLabelRes(ResourcesMediaType.BOOK))
    }

    @Test
    fun testBuildMetaLine() {
        val metaLineWithLang = ResourcesCardBinder.buildMetaLine(context, ResourcesMediaType.PDF, "English")
        assertEquals("${context.getString(R.string.filter_pdfs)} · English", metaLineWithLang)

        val metaLineNoLang = ResourcesCardBinder.buildMetaLine(context, ResourcesMediaType.BOOK, null)
        assertEquals(context.getString(R.string.filter_books), metaLineNoLang)
    }

    @Test
    fun `partialPercent is capped below 100 and unknown without a size`() {
        assertEquals(40, ResourcesCardBinder.partialPercent(40, 100))
        assertEquals(99, ResourcesCardBinder.partialPercent(100, 100))
        assertNull(ResourcesCardBinder.partialPercent(40, null))
        assertNull(ResourcesCardBinder.partialPercent(40, 0))
    }

    @Test
    fun `partialDownloadPercent reads the partial file against the recorded size`() = runTest {
        val dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val finalFile = File(temporaryFolder.newFolder("res"), "video.mp4")
        assertNull(ResourcesCardBinder.partialDownloadPercent(finalFile, dispatcherProvider))

        val tempFile = File(finalFile.parentFile, "video.mp4.tmp").apply { writeBytes(ByteArray(250)) }
        assertEquals(PartialDownload(null), ResourcesCardBinder.partialDownloadPercent(finalFile, dispatcherProvider))

        File(tempFile.parentFile, "video.mp4.tmp${DownloadService.EXPECTED_SIZE_SUFFIX}").writeText("1000")
        assertEquals(PartialDownload(25), ResourcesCardBinder.partialDownloadPercent(finalFile, dispatcherProvider))
    }

    @Test
    fun `partialLabel shows the percent when known`() {
        assertEquals(context.getString(R.string.download_paused_at_percent, 25), ResourcesCardBinder.partialLabel(context, PartialDownload(25)))
        assertEquals(context.getString(R.string.download_partially_downloaded), ResourcesCardBinder.partialLabel(context, PartialDownload(null)))
    }
}
