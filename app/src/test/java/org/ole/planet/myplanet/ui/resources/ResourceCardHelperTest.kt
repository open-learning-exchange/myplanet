package org.ole.planet.myplanet.ui.resources

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utils.LibraryType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ResourceCardHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testTypeColorResMapping() {
        assertEquals(R.color.type_pdf, ResourceCardHelper.typeColorRes(LibraryType.PDF))
        assertEquals(R.color.type_video, ResourceCardHelper.typeColorRes(LibraryType.VIDEO))
        assertEquals(R.color.type_audio, ResourceCardHelper.typeColorRes(LibraryType.AUDIO))
        assertEquals(R.color.type_book, ResourceCardHelper.typeColorRes(LibraryType.BOOK))
    }

    @Test
    fun testTypeIconResMapping() {
        assertEquals(R.drawable.ic_type_pdf, ResourceCardHelper.typeIconRes(LibraryType.PDF))
        assertEquals(R.drawable.ic_type_video, ResourceCardHelper.typeIconRes(LibraryType.VIDEO))
        assertEquals(R.drawable.ic_type_audio, ResourceCardHelper.typeIconRes(LibraryType.AUDIO))
        assertEquals(R.drawable.ic_type_book, ResourceCardHelper.typeIconRes(LibraryType.BOOK))
    }

    @Test
    fun testTypeLabelResMapping() {
        assertEquals(R.string.filter_pdfs, ResourceCardHelper.typeLabelRes(LibraryType.PDF))
        assertEquals(R.string.filter_videos, ResourceCardHelper.typeLabelRes(LibraryType.VIDEO))
        assertEquals(R.string.filter_audio, ResourceCardHelper.typeLabelRes(LibraryType.AUDIO))
        assertEquals(R.string.filter_books, ResourceCardHelper.typeLabelRes(LibraryType.BOOK))
    }

    @Test
    fun testBuildMetaLine() {
        val metaLineWithLang = ResourceCardHelper.buildMetaLine(context, LibraryType.PDF, "English")
        assertEquals("${context.getString(R.string.filter_pdfs)} · English", metaLineWithLang)

        val metaLineNoLang = ResourceCardHelper.buildMetaLine(context, LibraryType.BOOK, null)
        assertEquals(context.getString(R.string.filter_books), metaLineNoLang)
    }
}
