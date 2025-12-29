package org.ole.planet.myplanet.ui.widgets

import android.content.Context
import android.content.Intent
import android.text.style.ClickableSpan
import android.view.View
import org.ole.planet.myplanet.ui.viewer.WebViewActivity

class CustomClickableSpan(private val url: String, private val title: String, private val context: Context) : ClickableSpan() {
    override fun onClick(widget: View) {
        openLinkInWebView(url, title)
    }

    private fun openLinkInWebView(link: String?, title: String) {
        if (!link.isNullOrEmpty()) {
            val webViewIntent = Intent(context, WebViewActivity::class.java)
            webViewIntent.putExtra("title", title)
            webViewIntent.putExtra("link", link)
            context.startActivity(webViewIntent)
        } else {
            Utilities.toast(context, "Invalid link")
        }
    }
}
