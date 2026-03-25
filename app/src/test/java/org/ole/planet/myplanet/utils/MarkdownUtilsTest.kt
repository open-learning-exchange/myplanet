package org.ole.planet.myplanet.utils

import android.text.Layout
import android.text.style.AlignmentSpan
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.every
import io.mockk.mockk
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.RenderProps
import io.noties.markwon.html.HtmlTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class MarkdownUtilsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun prependBaseUrlToImages_handles_null_and_empty_input() {
        assertEquals("", MarkdownUtils.prependBaseUrlToImages(null, "http://base.url/"))
        assertEquals("", MarkdownUtils.prependBaseUrlToImages("", "http://base.url/"))
    }

    @Test
    fun prependBaseUrlToImages_replaces_markdown_images_correctly() {
        val markdown = "This is an image: ![alt text](resources/image.png)"
        val expected = "This is an image: <img src=http://base.url/image.png width=150 height=100/>"
        assertEquals(expected, MarkdownUtils.prependBaseUrlToImages(markdown, "http://base.url/"))
    }

    @Test
    fun prependBaseUrlToImages_replaces_multiple_markdown_images_correctly() {
        val markdown = "![alt](resources/img1.png) and ![alt2](img2.jpg)"
        val expected = "<img src=http://base.url/img1.png width=150 height=100/> and <img src=http://base.url/img2.jpg width=150 height=100/>"
        assertEquals(expected, MarkdownUtils.prependBaseUrlToImages(markdown, "http://base.url/"))
    }

    @Test
    fun prependBaseUrlToImages_uses_custom_width_and_height() {
        val markdown = "![alt text](resources/image.png)"
        val expected = "<img src=http://base.url/image.png width=300 height=200/>"
        assertEquals(expected, MarkdownUtils.prependBaseUrlToImages(markdown, "http://base.url/", 300, 200))
    }

    @Test
    fun AlignTagHandler_returns_correct_spans_for_attributes() {
        val handler = MarkdownUtils.AlignTagHandler()
        val configuration = mockk<MarkwonConfiguration>()
        val renderProps = mockk<RenderProps>()

        // Test center
        val tagCenter = mockk<HtmlTag> {
            every { attributes() } returns mapOf("center" to "")
        }
        val spanCenter = handler.getSpans(configuration, renderProps, tagCenter) as AlignmentSpan.Standard
        assertEquals(Layout.Alignment.ALIGN_CENTER, spanCenter.alignment)

        // Test end
        val tagEnd = mockk<HtmlTag> {
            every { attributes() } returns mapOf("end" to "")
        }
        val spanEnd = handler.getSpans(configuration, renderProps, tagEnd) as AlignmentSpan.Standard
        assertEquals(Layout.Alignment.ALIGN_OPPOSITE, spanEnd.alignment)

        // Test default
        val tagDefault = mockk<HtmlTag> {
            every { attributes() } returns mapOf("other" to "")
        }
        val spanDefault = handler.getSpans(configuration, renderProps, tagDefault) as AlignmentSpan.Standard
        assertEquals(Layout.Alignment.ALIGN_NORMAL, spanDefault.alignment)
    }

    @Test
    fun AlignTagHandler_supportedTags_returns_align() {
        val handler = MarkdownUtils.AlignTagHandler()
        assertTrue(handler.supportedTags().contains("align"))
    }
}
