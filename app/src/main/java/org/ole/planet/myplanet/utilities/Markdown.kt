package org.ole.planet.myplanet.utilities

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Layout
import android.text.Spannable
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.AlignmentSpan
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.RenderProps
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.html.HtmlTag
import io.noties.markwon.html.tag.SimpleTagHandler
import io.noties.markwon.image.ImageProps
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.file.FileSchemeHandler
import io.noties.markwon.image.network.NetworkSchemeHandler
import io.noties.markwon.image.network.OkHttpNetworkSchemeHandler
import io.noties.markwon.movement.MovementMethodPlugin
import org.commonmark.node.Image
import org.ole.planet.myplanet.R

object Markdown {
    private var currentZoomDialog: Dialog? = null

    fun create(context: Context): Markwon {
        return Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(MovementMethodPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create { plugin: HtmlPlugin -> plugin.addHandler(AlignTagHandler()) })
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configure(registry: MarkwonPlugin.Registry) {
                    registry.require(ImagesPlugin::class.java) { imagesPlugin ->
                        imagesPlugin.addSchemeHandler(FileSchemeHandler.createWithAssets(context.assets))
                        imagesPlugin.addSchemeHandler(NetworkSchemeHandler.create())
                        imagesPlugin.addSchemeHandler(OkHttpNetworkSchemeHandler.create())
                    }
                }

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
        textView.movementMethod = CustomLinkMovementMethod()
    }

    private class CustomImageSpan(private val theme: MarkwonTheme, private val url: String) : ClickableSpan() {
        override fun onClick(widget: View) {
            showZoomableImage(widget.context, url)
        }

        override fun updateDrawState(ds: TextPaint) {
            theme.applyLinkStyle(ds)
        }
    }

    private fun showZoomableImage(context: Context, imageUrl: String) {
        currentZoomDialog?.dismiss()

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        currentZoomDialog = dialog

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_zoomable_image, null)
        val photoView = view.findViewById<PhotoView>(R.id.photoView)
        val closeButton = view.findViewById<ImageView>(R.id.closeButton)

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))

        Glide.with(context)
            .load(imageUrl)
            .error(R.drawable.ic_loading)
            .into(photoView)

        closeButton.setOnClickListener {
            dialog.dismiss()
            currentZoomDialog = null
        }

        dialog.setOnDismissListener {
            currentZoomDialog = null
        }

        dialog.show()
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