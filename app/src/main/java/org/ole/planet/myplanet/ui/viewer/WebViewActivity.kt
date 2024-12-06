package org.ole.planet.myplanet.ui.viewer

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.databinding.ActivityWebViewBinding
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File

class WebViewActivity : AppCompatActivity() {
    private lateinit var activityWebViewBinding: ActivityWebViewBinding
    private var fromDeepLink = false
    private lateinit var link: String
    private var isLocalFile = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityWebViewBinding = ActivityWebViewBinding.inflate(layoutInflater)
        setContentView(activityWebViewBinding.root)
        val dataFromDeepLink = intent.dataString
        fromDeepLink = !TextUtils.isEmpty(dataFromDeepLink)
        val title: String? = intent.getStringExtra("title")
        link = intent.getStringExtra("link") ?: ""
        isLocalFile = intent.getBooleanExtra("isLocalFile", false)

        Log.d("WebViewActivity", "onCreate: $link, isLocalFile: $isLocalFile")
        clearCookie()

        if (!TextUtils.isEmpty(title)) {
            activityWebViewBinding.contentWebView.webTitle.text = title
        }

        activityWebViewBinding.contentWebView.pBar.max = 100
        activityWebViewBinding.contentWebView.pBar.progress = 0
        setListeners()
        activityWebViewBinding.contentWebView.wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(true)
            builtInZoomControls = true
        }

//        activityWebViewBinding.contentWebView.wv.settings.javaScriptEnabled = true
//        activityWebViewBinding.contentWebView.wv.settings.javaScriptCanOpenWindowsAutomatically = true
        if (isLocalFile) {
            val touchedFile = intent.getStringExtra("TOUCHED_FILE")
            if (!touchedFile.isNullOrEmpty()) {
                val localFilePath = File(MainApplication.context.getExternalFilesDir(null), touchedFile).absolutePath
                activityWebViewBinding.contentWebView.wv.loadUrl("file://$localFilePath")
            }
        } else {
            // Existing remote URL loading logic
            val headers = mapOf("Authorization" to Utilities.header)
            activityWebViewBinding.contentWebView.wv.loadUrl(link, headers)
        }
        activityWebViewBinding.contentWebView.finish.setOnClickListener { finish() }
        setWebClient()
    }


    private fun setWebClient() {
        activityWebViewBinding.contentWebView.wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url.endsWith("/eng/")) {
                    finish()
                }
                val i = Uri.parse(url)
                activityWebViewBinding.contentWebView.webSource.text = i.host
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
            }
        }
    }

    private fun clearCookie() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }

    private fun setListeners() {
        activityWebViewBinding.contentWebView.wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                activityWebViewBinding.contentWebView.pBar.progress = newProgress
                if (view.url?.endsWith("/eng/") == true) {
                    finish()
                }
                activityWebViewBinding.contentWebView.pBar.incrementProgressBy(newProgress)
                if (newProgress == 100 && activityWebViewBinding.contentWebView.pBar.isShown) {
                    activityWebViewBinding.contentWebView.pBar.visibility = View.GONE
                }
            }

            override fun onReceivedTitle(view: WebView, title: String) {
                activityWebViewBinding.contentWebView.webTitle.text = title
                super.onReceivedTitle(view, title)
            }
        }
    }

}
