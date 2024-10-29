package org.ole.planet.myplanet.utilities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.text.Layout
import android.text.style.AlignmentSpan
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.RenderProps
import io.noties.markwon.core.spans.LinkSpan
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

object Markdown {
    fun create(context: Context): Markwon {
        return Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(MovementMethodPlugin.none())
            .usePlugin(HtmlPlugin.create { plugin: HtmlPlugin -> plugin.addHandler(AlignTagHandler()) })
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configure(registry: MarkwonPlugin.Registry) {
                    registry.require(ImagesPlugin::class.java) { imagesPlugin ->
                        imagesPlugin.addSchemeHandler(FileSchemeHandler.create())
                        imagesPlugin.addSchemeHandler(NetworkSchemeHandler.create())
                        imagesPlugin.addSchemeHandler(OkHttpNetworkSchemeHandler.create())
                    }
                }

                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    builder.appendFactory(Image::class.java) { configuration, props ->
                        Log.d("okuro", "image clicked")
                        val url = ImageProps.DESTINATION.require(props)
                        LinkSpan(configuration.theme(), url, ImageLinkResolver(configuration.linkResolver()))
                    }
                }
            })
            .build()
    }

    fun setMarkdownText(textView: TextView, markdown: String) {
        val markwon = create(textView.context)
        markwon.setMarkdown(textView, markdown)
    }

    fun zoomImage(view: ImageView) {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 2f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 2f)
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY)
        animatorSet.duration = 300
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()

        view.setOnClickListener {
            // Toggle zoom on click
            if (view.scaleX > 1f) {
                scaleX.setFloatValues(2f, 1f)
                scaleY.setFloatValues(2f, 1f)
            } else {
                scaleX.setFloatValues(1f, 2f)
                scaleY.setFloatValues(1f, 2f)
            }
            animatorSet.start()
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

    class ImageLinkResolver(private val original: LinkResolver) : LinkResolver {
        override fun resolve(view: View, link: String) {
            if (view is ImageView) {
                zoomImage(view)
            } else {
                original.resolve(view, link)
            }
        }
    }
}