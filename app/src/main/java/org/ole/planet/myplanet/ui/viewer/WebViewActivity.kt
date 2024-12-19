package org.ole.planet.myplanet.ui.viewer

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.ole.planet.myplanet.databinding.ActivityWebViewBinding
import java.io.File

class WebViewActivity : AppCompatActivity() {
    private lateinit var activityWebViewBinding: ActivityWebViewBinding
    private var fromDeepLink = false
    private lateinit var link: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityWebViewBinding = ActivityWebViewBinding.inflate(layoutInflater)
        setContentView(activityWebViewBinding.root)
        val dataFromDeepLink = intent.dataString
        fromDeepLink = !TextUtils.isEmpty(dataFromDeepLink)
        val title: String? = intent.getStringExtra("title")
        link = intent.getStringExtra("link") ?: ""
        val resourceId = intent.getStringExtra("RESOURCE_ID")
        val localAddress = intent.getStringExtra("LOCAL_ADDRESS")

        clearCookie()
        if (!TextUtils.isEmpty(title)) {
            activityWebViewBinding.contentWebView.webTitle.text = title
        }
        activityWebViewBinding.contentWebView.pBar.max = 100
        activityWebViewBinding.contentWebView.pBar.progress = 0

        setupWebView()
        setListeners()

        if (resourceId != null) {
            val directory = File(getExternalFilesDir(null), "ole/$resourceId")
            val indexFile = File(directory, "index.html")
            Log.d("okuro", "$directory")
            Log.d("WebView", "Directory exists: ${directory.exists()}")
            Log.d("WebView", "Index file exists: ${indexFile.exists()}")
            Log.d("WebView", "Index file path: ${indexFile.absolutePath}")

            if (indexFile.exists()) {
                activityWebViewBinding.contentWebView.wv.loadUrl("file://${indexFile.absolutePath}")
            } else {
                Log.e("WebView", "Index file not found at ${indexFile.absolutePath}")
            }
        } else {
            activityWebViewBinding.contentWebView.wv.loadUrl(link)
        }

        activityWebViewBinding.contentWebView.finish.setOnClickListener { finish() }
        setWebClient()
    }

    private fun setupWebView() {
        activityWebViewBinding.contentWebView.wv.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            allowFileAccess = true
            domStorageEnabled = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            defaultTextEncodingName = "utf-8"
//            mixedContentMode = WebView.MIXED_CONTENT_ALWAYS_ALLOW
        }
    }

    private fun setWebClient() {
        activityWebViewBinding.contentWebView.wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("WebView", "Page started loading: $url")
                if (!url.startsWith("file://") && url.endsWith("/eng/")) {
                    finish()
                }
                if (url.startsWith("file://")) {
                    activityWebViewBinding.contentWebView.webSource.text = "Local Resource"
                } else {
                    val i = Uri.parse(url)
                    activityWebViewBinding.contentWebView.webSource.text = i.host
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Log.d("WebView", "Page finished loading: $url")
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e("WebView", "Error: $errorCode - $description, URL: $failingUrl")
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                Log.d("WebView", "Resource requested: $url")
                if (url.startsWith("file://")) {
                    val resourceId = intent.getStringExtra("RESOURCE_ID")
                    if (resourceId != null) {
                        val path = url.replace("file://${getExternalFilesDir(null)}/ole/$resourceId/", "")
                        val file = File(getExternalFilesDir(null), "ole/$resourceId/$path")
                        Log.d("WebView", "Looking for resource at: ${file.absolutePath}")
                        Log.d("WebView", "File exists: ${file.exists()}")
                    }
                }
                return super.shouldInterceptRequest(view, request)
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
                if (view.url?.startsWith("file://") == false && view.url?.endsWith("/eng/") == true) {
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

            override fun onConsoleMessage(message: String?, lineNumber: Int, sourceID: String?) {
                Log.d("WebView Console", "$message -- From line $lineNumber of $sourceID")
                super.onConsoleMessage(message, lineNumber, sourceID)
            }
        }
    }
}
