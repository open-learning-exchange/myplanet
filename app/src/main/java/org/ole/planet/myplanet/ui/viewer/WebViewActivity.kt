package org.ole.planet.myplanet.ui.viewer

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityWebViewBinding
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils

class WebViewActivity : AppCompatActivity() {
    private lateinit var activityWebViewBinding: ActivityWebViewBinding
    private var fromDeepLink = false
    private lateinit var link: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityWebViewBinding = ActivityWebViewBinding.inflate(layoutInflater)
        setContentView(activityWebViewBinding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, activityWebViewBinding.root)
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
            // Only enable JavaScript for local resources that need it
            val isLocalResource = intent.getStringExtra("RESOURCE_ID") != null
            javaScriptEnabled = isLocalResource
            javaScriptCanOpenWindowsAutomatically = false
            
            // File access settings - only allow for local resources
            allowFileAccess = isLocalResource
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            
            // Safe settings
            domStorageEnabled = true
            defaultTextEncodingName = "utf-8"
            
            // Security settings
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            
            // Disable geolocation
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                setGeolocationEnabled(false)
            }
            
            // Disable save password
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                setSavePassword(false)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                when (nightModeFlags) {
                    android.content.res.Configuration.UI_MODE_NIGHT_YES -> {
                        forceDark = WebSettings.FORCE_DARK_ON
                        activityWebViewBinding.contentWebView.webTitle.setTextColor(ContextCompat.getColor(this@WebViewActivity, com.mikepenz.materialize.R.color.md_white_1000))
                        activityWebViewBinding.contentWebView.webSource.setTextColor(ContextCompat.getColor(this@WebViewActivity, com.mikepenz.materialize.R.color.md_white_1000))
                        activityWebViewBinding.contentWebView.contentWebView.setBackgroundColor(ContextCompat.getColor(this@WebViewActivity, com.mikepenz.materialize.R.color.md_black_1000))
                    }

                    android.content.res.Configuration.UI_MODE_NIGHT_NO -> {
                        forceDark = WebSettings.FORCE_DARK_OFF
                        activityWebViewBinding.contentWebView.webTitle.setTextColor(ContextCompat.getColor(this@WebViewActivity, com.mikepenz.materialize.R.color.md_black_1000))
                        activityWebViewBinding.contentWebView.webSource.setTextColor(ContextCompat.getColor(this@WebViewActivity, com.mikepenz.materialize.R.color.md_black_1000))
                    }
                }
            }
        }
    }

    private fun setWebClient() {
        activityWebViewBinding.contentWebView.wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                
                // Validate URL before loading
                if (!isUrlSafe(url)) {
                    view.stopLoading()
                    finish()
                    return
                }
                
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
            
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return true
                
                // Use our comprehensive URL safety check
                return !isUrlSafe(url) // Block unsafe URLs, allow safe ones
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
    
    private fun isUrlSafe(url: String): Boolean {
        return try {
            val uri = url.toUri()
            when {
                // Allow HTTPS URLs
                uri.scheme == "https" -> true
                
                // Allow HTTP URLs only for trusted Planet servers
                uri.scheme == "http" -> isTrustedPlanetServer(uri.host)
                
                // Allow file URLs only for local resources and only from app's directory
                uri.scheme == "file" -> {
                    val resourceId = intent.getStringExtra("RESOURCE_ID")
                    if (resourceId != null) {
                        val appDir = getExternalFilesDir(null)?.absolutePath ?: ""
                        url.startsWith("file://$appDir")
                    } else {
                        false
                    }
                }
                // Block everything else
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isTrustedPlanetServer(host: String?): Boolean {
        if (host == null) return false

        val trustedUrls = listOfNotNull(
            BuildConfig.PLANET_LEARNING_URL.takeIf { it.isNotEmpty() },
            BuildConfig.PLANET_GUATEMALA_URL.takeIf { it.isNotEmpty() },
            BuildConfig.PLANET_SANPABLO_URL.takeIf { it.isNotEmpty() },
            BuildConfig.PLANET_SANPABLO_CLONE_URL.takeIf { it.isNotEmpty() },
            BuildConfig.PLANET_EARTH_URL.takeIf { it.isNotEmpty() },
            BuildConfig.PLANET_SOMALIA_URL.takeIf { it.isNotEmpty() },
            BuildConfig.PLANET_VI_URL.takeIf { it.isNotEmpty() },
            BuildConfig.PLANET_XELA_URL.takeIf { it.isNotEmpty() },
            BuildConfig.PLANET_URIUR_URL.takeIf { it.isNotEmpty() },
            BuildConfig.PLANET_URIUR_CLONE_URL.takeIf { it.isNotEmpty() },
            BuildConfig.PLANET_RUIRU_URL.takeIf { it.isNotEmpty() },
            BuildConfig.PLANET_EMBAKASI_URL.takeIf { it.isNotEmpty() },
            BuildConfig.PLANET_EMBAKASI_CLONE_URL.takeIf { it.isNotEmpty() },
            BuildConfig.PLANET_CAMBRIDGE_URL.takeIf { it.isNotEmpty() }
        )

        return trustedUrls.any { url ->
            host == url || host.endsWith(".$url")
        }
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
                val sanitizedTitle = title.take(100).filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".,!?-_" }
                activityWebViewBinding.contentWebView.webTitle.text = sanitizedTitle
                super.onReceivedTitle(view, sanitizedTitle)
            }

            override fun onConsoleMessage(message: String?, lineNumber: Int, sourceID: String?) {
                if (BuildConfig.DEBUG) {
                    super.onConsoleMessage(message, lineNumber, sourceID)
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                return false
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: android.webkit.GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, false, false)
            }
        }
    }
}
