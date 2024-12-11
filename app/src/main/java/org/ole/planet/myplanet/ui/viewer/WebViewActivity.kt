package org.ole.planet.myplanet.ui.viewer

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.databinding.ActivityWebViewBinding
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File

class WebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebViewBinding
    private var fromDeepLink = false
    private lateinit var link: String
    private var isLocalFile = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        extractIntentData()
        clearCookies()

        setupWebViewSettings()
        setupListeners()

        if (isLocalFile) {
            loadLocalFile()
        } else {
            loadRemoteUrl()
        }
        setupWebClient()
    }

    private fun extractIntentData() {
        fromDeepLink = !TextUtils.isEmpty(intent.dataString)
        link = intent.getStringExtra("link") ?: ""
        isLocalFile = intent.getBooleanExtra("isLocalFile", false)

        Log.d("webview", "Intent Data - link: $link, isLocalFile: $isLocalFile, fromDeepLink: $fromDeepLink")

        intent.getStringExtra("title")?.let {
            binding.contentWebView.webTitle.text = it
        }
    }

    private fun clearCookies() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        Log.d("webview", "Cookies cleared.")
    }

    private fun setupWebViewSettings() {
        with(binding.contentWebView.wv.settings) {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true

            domStorageEnabled = true

            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            cacheMode = WebSettings.LOAD_DEFAULT
            databaseEnabled = true

            useWideViewPort = true
            loadWithOverviewMode = true

            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            allowFileAccess = true
            allowContentAccess = true
        }
        Log.d("webview", "WebView settings configured.")
    }

    private fun loadLocalFile() {
        val touchedFile = intent.getStringExtra("TOUCHED_FILE")

        if (!touchedFile.isNullOrEmpty()) {
            val localFilePath = if (touchedFile.startsWith("file://")) {
                touchedFile
            } else {
                File(MainApplication.context.getExternalFilesDir(null), touchedFile).absolutePath
            }
            binding.contentWebView.wv.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    return false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    Log.d("webview", "Page finished loading: $url")
                }
            }
            binding.contentWebView.wv.loadUrl("file:///android_asset/index.html")
            Log.d("webview", "Loaded local file: file:///android_asset/index.html")
        } else {
            Log.w("webview", "TOUCHED_FILE is null or empty.")
        }
    }

    private fun loadRemoteUrl() {
        val headers = mapOf("Authorization" to Utilities.header)
        binding.contentWebView.wv.loadUrl("file:///android_asset/index.html") //link, headers
        Log.d("webview", "Loaded remote URL: $link with headers.")
    }

    private fun setupWebClient() {
        binding.contentWebView.wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("webview", "Page started loading: $url")

                if (url.endsWith("/eng/")) {
                    Log.d("webview", "Finishing activity due to /eng/ URL.")
                    finish()
                }

                val host = Uri.parse(url).host.orEmpty()
                binding.contentWebView.webSource.text = host
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Log.d("webview", "Page finished loading: $url")
            }
        }

        binding.contentWebView.wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.contentWebView.pBar.progress = newProgress
                if (newProgress == 100) {
                    binding.contentWebView.pBar.visibility = View.GONE
                }
                Log.d("webview", "Page loading progress: $newProgress%")
            }

            override fun onReceivedTitle(view: WebView, title: String) {
                binding.contentWebView.webTitle.text = title
                Log.d("webview", "Received page title: $title")
            }
        }
    }

    private fun setupListeners() {
        binding.contentWebView.finish.setOnClickListener {
            Log.d("webview", "Finish button clicked.")
            finish()
        }
    }
}
