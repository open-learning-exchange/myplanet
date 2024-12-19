package org.ole.planet.myplanet.ui.viewer

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.databinding.ActivityWebViewBinding
import org.ole.planet.myplanet.model.RealmAttachment
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class WebViewActivity : AppCompatActivity() {
    private lateinit var activityWebViewBinding: ActivityWebViewBinding
    private var fromDeepLink = false
    private lateinit var link: String
    private var isLocalFile = false
    var resourceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityWebViewBinding = ActivityWebViewBinding.inflate(layoutInflater)
        setContentView(activityWebViewBinding.root)
        val dataFromDeepLink = intent.dataString
        fromDeepLink = !TextUtils.isEmpty(dataFromDeepLink)
        val title: String? = intent.getStringExtra("title")
        link = intent.getStringExtra("link") ?: ""
        isLocalFile = intent.getBooleanExtra("isLocalFile", false)

        if (isLocalFile) {
            resourceId = intent.getStringExtra("RESOURCE_ID")
            Log.d("okuro", "$resourceId")
            if (!resourceId.isNullOrEmpty()) {
                lifecycleScope.launch {
                    loadLocalHtmlResource(resourceId)
                }
            }
        }

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

//        if (isLocalFile) {
//            val touchedFile = intent.getStringExtra("TOUCHED_FILE")
//            if (!touchedFile.isNullOrEmpty()) {
//                val localFilePath = File(MainApplication.context.getExternalFilesDir(null), touchedFile).absolutePath
//                activityWebViewBinding.contentWebView.wv.loadUrl("file://$localFilePath")
//            }
//        } else {
//            val headers = mapOf("Authorization" to Utilities.header)
//            activityWebViewBinding.contentWebView.wv.loadUrl(link, headers)
//        }
        activityWebViewBinding.contentWebView.finish.setOnClickListener { finish() }
        setWebClient()
    }

    private suspend fun loadLocalHtmlResource(resourceId: String?) {
        withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    activityWebViewBinding.contentWebView.pBar.visibility = View.VISIBLE
                }

                // Create resource directory structure
                val gamesDir = File(filesDir, "games")
                val resourceDir = File(gamesDir, resourceId)
                resourceDir.mkdirs()

                // Create necessary directories
                File(resourceDir, "js").mkdirs()
                File(resourceDir, "style").mkdirs()
                File(resourceDir, "style/fonts").mkdirs()
                File(resourceDir, "meta").mkdirs()

                // Copy all required files
                copyJsFiles(resourceDir)
                copyCssFiles(resourceDir)
                copyFontFiles(resourceDir)
                copyMetaFiles(resourceDir)

                // Get HTML content and write to file
                val htmlContent = retrieveHtmlContentFromRealm(resourceId)
                if (htmlContent != null) {
                    val htmlFile = File(resourceDir, "index.html")
                    htmlFile.writeText(htmlContent)

                    withContext(Dispatchers.Main) {
                        try {
                            // Load the file directly using file:// protocol
                            val fileUrl = "file://${htmlFile.absolutePath}"
                            Log.d("WebViewActivity", "Loading URL: $fileUrl")

                            activityWebViewBinding.contentWebView.wv.settings.apply {
                                allowFileAccess = true
                                allowFileAccessFromFileURLs = true
                                allowUniversalAccessFromFileURLs = true
                                domStorageEnabled = true
                                javaScriptEnabled = true
                            }

                            activityWebViewBinding.contentWebView.wv.loadUrl(fileUrl)
                        } catch (e: Exception) {
                            Log.e("WebViewActivity", "Error loading HTML file", e)
                            e.printStackTrace()
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("WebViewActivity", "Error in loadLocalHtmlResource", e)
            } finally {
                withContext(Dispatchers.Main) {
                    activityWebViewBinding.contentWebView.pBar.visibility = View.GONE
                }
            }
        }
    }

    private suspend fun copyJsFiles(resourceDir: File) {
        withContext(Dispatchers.IO) {
            val jsDir = File(resourceDir, "js")
            copyFiles(resourceDir, "js/", "application/javascript", jsDir)
        }
    }

    private suspend fun copyCssFiles(resourceDir: File) {
        withContext(Dispatchers.IO) {
            val styleDir = File(resourceDir, "style")
            copyFiles(resourceDir, "style/", "text/css", styleDir)
        }
    }

    private suspend fun copyFontFiles(resourceDir: File) {
        withContext(Dispatchers.IO) {
            val fontsDir = File(resourceDir, "style/fonts")
            val fontTypes = listOf(
                "application/font-woff",
                "application/vnd.ms-fontobject",
                "image/svg+xml"
            )
            fontTypes.forEach { type ->
                copyFiles(resourceDir, "style/fonts/", type, fontsDir)
            }
        }
    }

    private suspend fun copyMetaFiles(resourceDir: File) {
        withContext(Dispatchers.IO) {
            val metaDir = File(resourceDir, "meta")
            copyFiles(resourceDir, "meta/", "image/png", metaDir)
        }
    }

    private suspend fun copyFiles(resourceDir: File, prefix: String, contentType: String, destDir: File) {
        val realm = Realm.getDefaultInstance()
        try {
            val libraryItem = realm.where(RealmMyLibrary::class.java)
                .equalTo("_id", resourceId)
                .findFirst()

            val attachments = libraryItem?.attachments?.filter {
                it.name?.startsWith(prefix) == true && it.contentType == contentType
            }

            attachments?.forEach { attachment ->
                val fileName = attachment.name?.substringAfterLast("/")
                if (fileName != null) {
                    val destFile = File(destDir, fileName)

                    val fullAttachment = realm.where(RealmAttachment::class.java)
                        .equalTo("id", attachment.id)
                        .findFirst()

                    fullAttachment?.let {
                        val fileContent = readAttachmentContent(it)
                        if (fileContent != null) {
                            destFile.writeText(fileContent)
                            Log.d("WebViewActivity", "Copied file: ${attachment.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WebViewActivity", "Error copying files for prefix $prefix", e)
        } finally {
            realm.close()
        }
    }

    private suspend fun retrieveHtmlContentFromRealm(resourceId: String?): String? {
        return withContext(Dispatchers.IO) {
            val realm = Realm.getDefaultInstance()
            try {
                val libraryItem = realm.where(RealmMyLibrary::class.java)
                    .equalTo("_id", resourceId)
                    .findFirst()

                val htmlAttachment = libraryItem?.attachments?.firstOrNull {
                    it.name == "index.html"
                }

                Log.d("okuro", "htmlAttachment: $htmlAttachment")

                htmlAttachment?.let { attachment ->
                    realm.where(RealmAttachment::class.java)
                        .equalTo("id", attachment.id)
                        .findFirst()
                        ?.let { fullAttachment ->
                            readAttachmentContent(fullAttachment)
                        }
                }
            } catch (e: Exception) {
                Log.e("WebViewActivity", "Error retrieving HTML from Realm", e)
                null
            } finally {
                realm.close()
            }
        }
    }

    private suspend fun readAttachmentContent(attachment: RealmAttachment): String? {
        return withContext(Dispatchers.IO) {
            val file = File(MainApplication.context.getExternalFilesDir(null), attachment.name)
            Log.d("WebViewActivity", "Checking file: ${file.absolutePath}, exists: ${file.exists()}")
            if (file.exists()) {
                file.readText()
            } else {
                null
            }
        }
    }

    private suspend fun ensureResourceDownloaded(resourceId: String?) {
        if (resourceId == null) return

        withContext(Dispatchers.IO) {
            val resourceDir = createResourceDirectoryStructure(resourceId)
            val realm = Realm.getDefaultInstance()

            try {
                Log.d("WebViewActivity", "Looking for resource with ID: $resourceId")
                val libraryItem = realm.where(RealmMyLibrary::class.java)
                    .equalTo("_id", resourceId)
                    .findFirst()

                if (libraryItem == null) {
                    Log.e("WebViewActivity", "Library item not found for ID: $resourceId")
                    return@withContext
                }

                Log.d("WebViewActivity", "Found library item with ${libraryItem.attachments?.size} attachments")

                libraryItem.attachments?.forEach { attachment ->
                    if (attachment.isStub) {
                        val destFile = File(resourceDir, attachment.name)
                        if (!destFile.exists()) {
                            Log.d("WebViewActivity", "Attempting to download: ${attachment.name}")
                            try {
                                val content = downloadAttachmentContent(attachment)
                                if (content != null) {
                                    destFile.parentFile?.mkdirs()
                                    destFile.writeText(content)
                                    Log.d("WebViewActivity", "Successfully saved: ${attachment.name}")
                                } else {
                                    Log.e("WebViewActivity", "Failed to download: ${attachment.name}")
                                }
                            } catch (e: Exception) {
                                Log.e("WebViewActivity", "Error downloading ${attachment.name}", e)
                            }
                        } else {
                            Log.d("WebViewActivity", "File already exists: ${attachment.name}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WebViewActivity", "Error in ensureResourceDownloaded", e)
            } finally {
                realm.close()
            }
        }
    }

    private suspend fun downloadAttachmentContent(attachment: RealmAttachment): String? {
        return withContext(Dispatchers.IO) {
            val realm = Realm.getDefaultInstance()
            try {
                val libraryItem = realm.where(RealmMyLibrary::class.java)
                    .equalTo("_id", resourceId)
                    .findFirst()

                Log.d("WebViewActivity", "Found library item: ${libraryItem?._id}")
                Log.d("WebViewActivity", "Remote address: ${libraryItem?.resourceRemoteAddress}")

                val remoteAddress = libraryItem?.resourceRemoteAddress
                if (remoteAddress == null) {
                    Log.e("WebViewActivity", "Remote address not found for resource")
                    return@withContext null
                }

                val baseUrl = if (remoteAddress.contains("@")) {
                    val urlParts = remoteAddress.split("@")
                    val auth = urlParts[0]
                    val restOfUrl = urlParts[1]
                    "${Utilities.getUrl()}$restOfUrl"
                } else {
                    remoteAddress
                }.substringBeforeLast("/")

                val attachmentUrl = "$baseUrl/${attachment.name}"
                Log.d("WebViewActivity", "Attempting to download from: $attachmentUrl")

                try {
                    val url = URL(attachmentUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"

                    if (remoteAddress.contains("@")) {
                        val auth = remoteAddress.split("@")[0].split("://")[1]
                        val base64Auth = android.util.Base64.encodeToString(
                            auth.toByteArray(),
                            android.util.Base64.NO_WRAP
                        )
                        connection.setRequestProperty("Authorization", "Basic $base64Auth")
                    }

                    try {
                        val responseCode = connection.responseCode
                        Log.d("WebViewActivity", "Response code for ${attachment.name}: $responseCode")

                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            val content = connection.inputStream.bufferedReader().use { it.readText() }

                            val localFile = File(MainApplication.context.getExternalFilesDir(null), attachment.name)
                            localFile.parentFile?.mkdirs()
                            localFile.writeText(content)

                            Log.d("WebViewActivity", "Successfully downloaded ${attachment.name}")
                            return@withContext content
                        } else {
                            val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                            Log.e("WebViewActivity", "Failed to download attachment: HTTP $responseCode")
                            Log.e("WebViewActivity", "Error response: $errorStream")
                            return@withContext null
                        }
                    } catch (e: Exception) {
                        Log.e("WebViewActivity", "Connection error for ${attachment.name}", e)
                        return@withContext null
                    } finally {
                        connection.disconnect()
                    }
                } catch (e: Exception) {
                    Log.e("WebViewActivity", "Error processing URL for ${attachment.name}", e)
                    return@withContext null
                }
            } catch (e: Exception) {
                Log.e("WebViewActivity", "Error in Realm query", e)
                return@withContext null
            } finally {
                realm.close()
            }
        }
    }

    private fun createResourceDirectoryStructure(resourceId: String): File {
        val gamesDir = File(filesDir, "games")
        gamesDir.mkdirs()

        val resourceDir = File(gamesDir, resourceId)
        resourceDir.mkdirs()

        File(resourceDir, "js").mkdirs()
        File(resourceDir, "style").mkdirs()
        File(resourceDir, "style/fonts").mkdirs()
        File(resourceDir, "meta").mkdirs()

        return resourceDir
    }

    private fun setWebClient() {
        activityWebViewBinding.contentWebView.wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (request == null) return null

                val url = request.url.toString()
                Log.d("WebViewActivity", "Intercepting request: $url")

                val gamesDir = File(filesDir, "games")
                val resourceDir = File(gamesDir, resourceId)

                try {
                    // Extract the path from the URL
                    val path = url.substringAfter("file://")
                    val file = File(path)

                    if (file.exists()) {
                        val mimeType = when {
                            url.endsWith(".js") -> "application/javascript"
                            url.endsWith(".css") -> "text/css"
                            url.endsWith(".woff") -> "application/font-woff"
                            url.endsWith(".eot") -> "application/vnd.ms-fontobject"
                            url.endsWith(".svg") -> "image/svg+xml"
                            url.endsWith(".png") -> "image/png"
                            else -> "text/plain"
                        }
                        return WebResourceResponse(mimeType, "UTF-8", file.inputStream())
                    }

                    Log.d("WebViewActivity", "Resource not found: $url")
                } catch (e: Exception) {
                    Log.e("WebViewActivity", "Error intercepting request: $url", e)
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url.endsWith("/eng/")) {
                    finish()
                }
                val i = Uri.parse(url)
                activityWebViewBinding.contentWebView.webSource.text = i.host
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("WebViewActivity", "Page finished loading: $url")
                activityWebViewBinding.contentWebView.pBar.visibility = View.GONE
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
