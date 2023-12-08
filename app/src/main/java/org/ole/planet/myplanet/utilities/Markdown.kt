package org.ole.planet.myplanet.utilities

import android.content.Context
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.html.HtmlPlugin
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
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configure(registry: MarkwonPlugin.Registry) {
                    registry.require(ImagesPlugin::class.java) { imagesPlugin ->
                        imagesPlugin.addSchemeHandler(FileSchemeHandler.create())
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
}