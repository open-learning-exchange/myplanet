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
class ResourcesCardBinderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testTypeColorResMapping() {
        assertEquals(R.color.type_pdf, ResourcesCardBinder.typeColorRes(LibraryType.PDF))
        assertEquals(R.color.type_video, ResourcesCardBinder.typeColorRes(LibraryType.VIDEO))
        assertEquals(R.color.type_audio, ResourcesCardBinder.typeColorRes(LibraryType.AUDIO))
        assertEquals(R.color.type_book, ResourcesCardBinder.typeColorRes(LibraryType.BOOK))
    }

    @Test
    fun testTypeIconResMapping() {
        assertEquals(R.drawable.ic_type_pdf, ResourcesCardBinder.typeIconRes(LibraryType.PDF))
        assertEquals(R.drawable.ic_type_video, ResourcesCardBinder.typeIconRes(LibraryType.VIDEO))
        assertEquals(R.drawable.ic_type_audio, ResourcesCardBinder.typeIconRes(LibraryType.AUDIO))
        assertEquals(R.drawable.ic_type_book, ResourcesCardBinder.typeIconRes(LibraryType.BOOK))
    }

    @Test
    fun testTypeLabelResMapping() {
        assertEquals(R.string.filter_pdfs, ResourcesCardBinder.typeLabelRes(LibraryType.PDF))
        assertEquals(R.string.filter_videos, ResourcesCardBinder.typeLabelRes(LibraryType.VIDEO))
        assertEquals(R.string.filter_audio, ResourcesCardBinder.typeLabelRes(LibraryType.AUDIO))
        assertEquals(R.string.filter_books, ResourcesCardBinder.typeLabelRes(LibraryType.BOOK))
    }

    @Test
    fun testBuildMetaLine() {
        val metaLineWithLang = ResourcesCardBinder.buildMetaLine(context, LibraryType.PDF, "English")
        assertEquals("${context.getString(R.string.filter_pdfs)} · English", metaLineWithLang)

        val metaLineNoLang = ResourcesCardBinder.buildMetaLine(context, LibraryType.BOOK, null)
        assertEquals(context.getString(R.string.filter_books), metaLineNoLang)
    }
}
