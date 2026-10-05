package org.ole.planet.myplanet.ui.resources

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
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
}
