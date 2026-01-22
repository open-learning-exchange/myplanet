package org.ole.planet.myplanet.ui.components

import android.content.Context
import android.content.Intent
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Toast
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
            Toast.makeText(context, "Invalid link", Toast.LENGTH_SHORT).show()
        }
    }
}
