package org.ole.planet.myplanet.ui.viewer

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import org.ole.planet.myplanet.databinding.ActivityWebViewBinding

class WebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebViewBinding
    private lateinit var link: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        extractIntentData()
        clearCookies()
        setupWebViewSettings()
        setupListeners()
        setupWebClient()

        // Load the URL directly
        binding.contentWebView.wv.loadUrl(link)
    }

    private fun extractIntentData() {
        link = intent.getStringExtra("link") ?: "https://example.com" // Default URL
        Log.d("webview", "Intent Data - link: $link")

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
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = true
        }
        Log.d("webview", "WebView settings configured.")
    }

    private fun setupWebClient() {
        binding.contentWebView.wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("webview", "Page started loading: $url")
                binding.contentWebView.pBar.visibility = View.VISIBLE
                binding.contentWebView.webSource.text = Uri.parse(url).host.orEmpty()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Log.d("webview", "Page finished loading: $url")
                binding.contentWebView.pBar.visibility = View.GONE
            }
        }

        binding.contentWebView.wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.contentWebView.pBar.progress = newProgress
                if (newProgress == 100) {
                    binding.contentWebView.pBar.visibility = View.GONE
                } else {
                    binding.contentWebView.pBar.visibility = View.VISIBLE
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
