package org.ole.planet.myplanet.utils

import android.content.Context
import android.text.Layout
import android.text.Spannable
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.AlignmentSpan
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.bumptech.glide.Glide
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.RenderProps
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.html.HtmlTag
import io.noties.markwon.html.tag.SimpleTagHandler
import io.noties.markwon.image.ImageProps
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.movement.MovementMethodPlugin
import java.util.regex.Pattern
import org.commonmark.node.Image

object MarkdownUtils {
    @Volatile private var markwonInstance: Markwon? = null
    private val imagePattern = Pattern.compile("!\\[.*?]\\((.*?)\\)")
    private val linkMovementMethod = CustomLinkMovementMethod()

    fun warmUp(context: Context) {
        if (markwonInstance == null) {
            create(context)
        }
    }

    fun create(context: Context): Markwon {
        return markwonInstance ?: synchronized(this) {
            markwonInstance ?: buildMarkwon(context.applicationContext).also { markwonInstance = it }
        }
    }

    private fun buildMarkwon(context: Context): Markwon {
        return Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(GlideImagesPlugin.create(Glide.with(context)))
            .usePlugin(MovementMethodPlugin.create(LinkMovementMethod.getInstance()))
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create { plugin: HtmlPlugin -> plugin.addHandler(AlignTagHandler()) })
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    builder.appendFactory(Image::class.java) { configuration, props ->
                        val url = ImageProps.DESTINATION.require(props)
                        CustomImageSpan(configuration.theme(), url)
                    }
                }
            }).build()
    }

    fun setMarkdownText(textView: TextView, markdown: String) {
        val markwon = create(textView.context)
        markwon.setMarkdown(textView, markdown)
        textView.movementMethod = linkMovementMethod
    }

    private class CustomImageSpan(private val theme: MarkwonTheme, private val url: String) : ClickableSpan() {
        override fun onClick(widget: View) {
            ImageViewerUtils.showZoomableImage(widget.context, url)
        }

        override fun updateDrawState(ds: TextPaint) {
            theme.applyLinkStyle(ds)
        }
    }

    fun prependBaseUrlToImages(
        markdownContent: String?,
        baseUrl: String,
        width: Int = 150,
        height: Int = 100
    ): String {
        val content = markdownContent ?: return markdownContent.orEmpty()
        val matcher = imagePattern.matcher(content)
        val result = StringBuilder()
        var last = 0
        while (matcher.find()) {
            result.append(content, last, matcher.start())
            val relativePath = matcher.group(1)
            val modifiedPath = if (relativePath != null && relativePath.startsWith("resources/")) {
                relativePath.substring("resources/".length)
            } else {
                relativePath
            }
            val fullUrl = baseUrl + modifiedPath
            result.append("<img src=$fullUrl width=$width height=$height/>")
            last = matcher.end()
        }
        result.append(content, last, content.length)
        return result.toString()
    }

    private class CustomLinkMovementMethod : LinkMovementMethod() {
        override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                var x = event.x.toInt()
                var y = event.y.toInt()

                x -= widget.totalPaddingLeft
                y -= widget.totalPaddingTop

                x += widget.scrollX
                y += widget.scrollY

                val layout = widget.layout
                val line = layout.getLineForVertical(y)
                val offset = layout.getOffsetForHorizontal(line, x.toFloat())

                val imageSpans = buffer.getSpans(offset, offset, CustomImageSpan::class.java)
                if (imageSpans.isNotEmpty()) {
                    imageSpans[0].onClick(widget)
                    return true
                }
            }
            return super.onTouchEvent(widget, buffer, event)
        }
    }

    class AlignTagHandler : SimpleTagHandler() {
        override fun getSpans(configuration: MarkwonConfiguration, renderProps: RenderProps, tag: HtmlTag): Any {
            val alignment: Layout.Alignment = if (tag.attributes().containsKey("center")) {
                Layout.Alignment.ALIGN_CENTER
            } else if (tag.attributes().containsKey("end")) {
                Layout.Alignment.ALIGN_OPPOSITE
            } else {
                Layout.Alignment.ALIGN_NORMAL
            }
            return AlignmentSpan.Standard(alignment)
        }

        override fun supportedTags(): Collection<String> {
            return setOf("align")
        }
    }
}
