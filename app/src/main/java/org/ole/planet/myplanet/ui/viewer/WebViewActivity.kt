package org.ole.planet.myplanet.ui.viewer

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityWebViewBinding
import java.io.File
import androidx.core.net.toUri

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

            if (indexFile.exists()) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                activityWebViewBinding.contentWebView.wv.loadUrl("file://${indexFile.absolutePath}")
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                when (nightModeFlags) {
                    android.content.res.Configuration.UI_MODE_NIGHT_YES -> {
                        forceDark = WebSettings.FORCE_DARK_ON
                        activityWebViewBinding.contentWebView.webTitle.setTextColor(ContextCompat.getColor(this@WebViewActivity, R.color.md_white_1000))
                        activityWebViewBinding.contentWebView.webSource.setTextColor(ContextCompat.getColor(this@WebViewActivity, R.color.md_white_1000))
                        activityWebViewBinding.contentWebView.contentWebView.setBackgroundColor(ContextCompat.getColor(this@WebViewActivity, R.color.md_black_1000))
                    }

                    android.content.res.Configuration.UI_MODE_NIGHT_NO -> {
                        forceDark = WebSettings.FORCE_DARK_OFF
                        activityWebViewBinding.contentWebView.webTitle.setTextColor(ContextCompat.getColor(this@WebViewActivity, R.color.md_black_1000))
                        activityWebViewBinding.contentWebView.webSource.setTextColor(ContextCompat.getColor(this@WebViewActivity, R.color.md_black_1000))
                    }
                }
            }
        }
    }

    private fun setWebClient() {
        activityWebViewBinding.contentWebView.wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (!url.startsWith("file://") && url.endsWith("/eng/")) {
                    finish()
                }
                if (url.startsWith("file://")) {
                    activityWebViewBinding.contentWebView.webSource.text = getString(R.string.local_resource)
                } else {
                    val i = url.toUri()
                    activityWebViewBinding.contentWebView.webSource.text = i.host
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                    view.evaluateJavascript(
                        """
                            (function() {
                                document.documentElement.setAttribute('dark', 'true');
                                document.documentElement.style.backgroundColor = '#000';
                                document.documentElement.style.color = '#FFF';
                                const elements = document.querySelectorAll('*');
                                elements.forEach(el => {
                                    if (window.getComputedStyle(el).color === 'rgb(0, 0, 0)') {
                                        el.style.color = '#FFF';
                                    }
                                });
                            })();
                            """.trimIndent(),
                        null
                    )
                } else {
                    view.evaluateJavascript(
                        """
                            (function() {
                                document.documentElement.removeAttribute('dark');
                                document.documentElement.style.backgroundColor = '#FFF';
                                document.documentElement.style.color = '#000';
                                const elements = document.querySelectorAll('*');
                                elements.forEach(el => {
                                    if (window.getComputedStyle(el).color === 'rgb(255, 255, 255)') {
                                        el.style.color = '#000';
                                    }
                                });
                            })();
                            """.trimIndent(),
                        null
                    )
                }
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
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
                super.onConsoleMessage(message, lineNumber, sourceID)
            }
        }
    }
}
