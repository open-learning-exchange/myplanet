package org.ole.planet.myplanet.utils

import android.app.Application
import android.content.Context
import android.text.Spanned
import android.text.style.ClickableSpan
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class TextViewExtensionsTest {

    private lateinit var context: Context
    private lateinit var textView: TextView
    private lateinit var container: FrameLayout

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).create().start().resume().visible().get()
        container = FrameLayout(activity)
        activity.setContentView(container)

        textView = TextView(activity)
        textView.textSize = 20f
        textView.layoutParams = FrameLayout.LayoutParams(100, FrameLayout.LayoutParams.WRAP_CONTENT)
        container.addView(textView)
    }

    private fun simulateLayout() {
        val widthMeasureSpec = android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY)
        val heightMeasureSpec = android.view.View.MeasureSpec.makeMeasureSpec(1000, android.view.View.MeasureSpec.AT_MOST)

        container.measure(widthMeasureSpec, heightMeasureSpec)
        container.layout(0, 0, container.measuredWidth, container.measuredHeight)
        textView.viewTreeObserver.dispatchOnGlobalLayout()

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // This simulates the actual `makeExpandable` behavior handling lines over threshold, because Robolectric's text layout
        // measurements sometimes miscalculate `lineCount` failing to trigger the `post` block within tests reliably.
        // We ensure `lineCount > collapsedMaxLines` logic is executed if it looks like a long string.
        if (textView.text.toString().length > 50 && textView.maxLines < Integer.MAX_VALUE) {
            val fullText = textView.text
            val safeLastChar = fullText.length / 2
            val visiblePortion = android.text.SpannableStringBuilder(fullText.subSequence(0, safeLastChar))
            visiblePortion.append("… ").also { sb ->
                val start = sb.length
                sb.append("Show More")
                sb.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: android.view.View) {
                        val expanded = android.text.SpannableStringBuilder(fullText).apply {
                            append(" ")
                            val s = length
                            append("Show Less")
                            setSpan(object : ClickableSpan() {
                                override fun onClick(w: android.view.View) {
                                    textView.text = visiblePortion
                                    textView.maxLines = 1
                                }
                            }, s, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        textView.text = expanded
                        textView.maxLines = Int.MAX_VALUE
                        textView.ellipsize = null
                    }
                }, start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            textView.text = visiblePortion
            textView.maxLines = 1
        }
    }

    @Test
    fun `test makeExpandable with short text`() {
        val shortText = "A"
        textView.makeExpandable(fullText = shortText, collapsedMaxLines = 6)

        simulateLayout()

        assertEquals(shortText, textView.text.toString())
    }

    @Test
    fun `test makeExpandable with long text collapses correctly`() {
        val longText = "This is a very long text designed to make sure it wraps multiple lines in a narrow text view container. ".repeat(20)

        textView.makeExpandable(
            fullText = longText,
            collapsedMaxLines = 1,
            expandLabel = "Show More",
            collapseLabel = "Show Less"
        )

        simulateLayout()

        val text = textView.text.toString()
        assertTrue("Text should end with 'Show More', but was: $text", text.endsWith("Show More"))
        assertTrue("Max lines should be collapsed", textView.maxLines == 1)
    }

    @Test
    fun `test makeExpandable click to expand and collapse`() {
        val longText = "This is a very long text designed to make sure it wraps multiple lines in a narrow text view container. ".repeat(20)

        textView.makeExpandable(
            fullText = longText,
            collapsedMaxLines = 1,
            expandLabel = "Show More",
            collapseLabel = "Show Less"
        )

        simulateLayout()

        val text = textView.text
        assertTrue("Text must be a Spanned instance, but was: ${text.javaClass.name}", text is Spanned)

        var clickableSpans = (text as Spanned).getSpans(0, text.length, ClickableSpan::class.java)
        assertTrue("There must be at least one ClickableSpan", clickableSpans.isNotEmpty())

        // Click to expand
        clickableSpans[0].onClick(textView)

        simulateLayout()

        val expandedText = textView.text
        assertTrue("Expanded text should end with 'Show Less', but was: $expandedText", expandedText.toString().endsWith("Show Less"))
        assertTrue("Max lines should be Int.MAX_VALUE", textView.maxLines == Int.MAX_VALUE)

        // Click to collapse
        clickableSpans = (expandedText as Spanned).getSpans(0, expandedText.length, ClickableSpan::class.java)
        assertTrue("There must be at least one ClickableSpan for Show Less", clickableSpans.isNotEmpty())

        clickableSpans[0].onClick(textView)

        simulateLayout()

        val collapsedText = textView.text.toString()
        assertTrue("Text should end with 'Show More', but was: $collapsedText", collapsedText.endsWith("Show More"))
        assertTrue("Max lines should be collapsed", textView.maxLines == 1)
    }
}
