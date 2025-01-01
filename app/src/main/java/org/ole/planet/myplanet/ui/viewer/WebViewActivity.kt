package org.ole.planet.myplanet.ui.viewer

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.ole.planet.myplanet.databinding.ActivityWebViewBinding

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
        if (title == "2048") {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        clearCookie()
        if (!TextUtils.isEmpty(title)) {
            activityWebViewBinding.contentWebView.webTitle.text = title
        }
        activityWebViewBinding.contentWebView.pBar.max = 100
        activityWebViewBinding.contentWebView.pBar.progress = 0
        activityWebViewBinding.contentWebView.finish.setOnClickListener { finish() }
        setWebClient()
    }

    private fun setupWebView() {
        val webSettings: WebSettings = activityWebViewBinding.contentWebView.wv.settings
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            when (nightModeFlags) {
                android.content.res.Configuration.UI_MODE_NIGHT_YES -> {
                    webSettings.forceDark = WebSettings.FORCE_DARK_ON
                    activityWebViewBinding.contentWebView.webTitle.setTextColor(resources.getColor(android.R.color.white))
                    activityWebViewBinding.contentWebView.webSource.setTextColor(resources.getColor(android.R.color.white))
                    activityWebViewBinding.contentWebView.contentWebView.setBackgroundColor(resources.getColor(android.R.color.black))
                }
                android.content.res.Configuration.UI_MODE_NIGHT_NO -> {
                    webSettings.forceDark = WebSettings.FORCE_DARK_OFF

                    activityWebViewBinding.contentWebView.webTitle.setTextColor(resources.getColor(android.R.color.black))
                    activityWebViewBinding.contentWebView.webSource.setTextColor(resources.getColor(android.R.color.black))
                }
            }
        }
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
