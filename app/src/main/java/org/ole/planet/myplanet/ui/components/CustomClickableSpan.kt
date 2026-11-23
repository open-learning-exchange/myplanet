package org.ole.planet.myplanet.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Toast
import androidx.core.net.toUri
import org.ole.planet.myplanet.ui.viewer.WebViewActivity

class CustomClickableSpan(private val url: String, private val title: String, private val context: Context) : ClickableSpan() {
    override fun onClick(widget: View) {
        openLink(url, title)
    }

    private fun openLink(link: String?, title: String) {
        if (link.isNullOrBlank()) {
            Toast.makeText(context, "Invalid link", Toast.LENGTH_SHORT).show()
            return
        }
        val normalized = normalizeWebLink(link.trim())
        if (normalized != null && openExternally(normalized)) return
        openInWebView(normalized ?: link, title)
    }

    private fun openExternally(link: String): Boolean {
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun openInWebView(link: String, title: String) {
        val webViewIntent = Intent(context, WebViewActivity::class.java)
        webViewIntent.putExtra("title", title)
        webViewIntent.putExtra("link", link)
        context.startActivity(webViewIntent)
    }

    companion object {
        internal fun normalizeWebLink(link: String): String? {
            val scheme = link.substringBefore(":", missingDelimiterValue = "").lowercase()
            return when {
                scheme == "http" || scheme == "https" -> link
                scheme.isEmpty() && !link.startsWith("/") && !link.startsWith("#") &&
                    link.substringBefore("/").contains(".") -> "https://$link"
                else -> null
            }
        }
    }
}
