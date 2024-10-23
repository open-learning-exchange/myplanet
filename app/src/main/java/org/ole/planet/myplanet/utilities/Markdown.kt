package org.ole.planet.myplanet.utilities

import android.content.Context
import android.text.Layout
import android.text.style.AlignmentSpan
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.RenderProps
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.html.HtmlTag
import io.noties.markwon.html.tag.SimpleTagHandler
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.file.FileSchemeHandler
import io.noties.markwon.image.network.NetworkSchemeHandler
import io.noties.markwon.image.network.OkHttpNetworkSchemeHandler
import io.noties.markwon.movement.MovementMethodPlugin

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
                        imagesPlugin.addSchemeHandler(FileSchemeHandler.createWithAssets(context.assets))
                        imagesPlugin.addSchemeHandler(NetworkSchemeHandler.create())
                        imagesPlugin.addSchemeHandler(OkHttpNetworkSchemeHandler.create())
                    }
                }
            })
            .build()
    }

    fun setMarkdownText(textView: TextView, markdown: String) {
        val markwon = create(textView.context)
        markwon.setMarkdown(textView, markdown)
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