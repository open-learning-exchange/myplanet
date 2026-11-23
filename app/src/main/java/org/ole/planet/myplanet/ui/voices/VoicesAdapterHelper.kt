package org.ole.planet.myplanet.ui.voices

import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.services.DownloadService
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.TextRevealer
import org.ole.planet.myplanet.utils.Utilities

object VoicesAdapterHelper {
    private val inlineResourceImage = Regex("!\\[[^]]*]\\(\\s*resources/([^)\\s]*)\\s*\\)")
    fun createOnAnimateTyping(
        scope: CoroutineScope,
        dispatcherProvider: DispatcherProvider
    ): (String, (String) -> Unit, () -> Unit) -> (() -> Unit) {
        return { response, onUpdate, onComplete ->
            val job = scope.launch(dispatcherProvider.main) {
                TextRevealer.reveal(response, onUpdate)
                onComplete()
            }
            val cancelJob: () -> Unit = { job.cancel() }
            cancelJob
        }
    }

    fun handleShareNewsResult(context: Context, result: Result<Unit>) {
        if (result.isSuccess) {
            Utilities.toast(context, context.getString(R.string.shared_to_community))
        } else {
            Utilities.toast(context, "Failed to share news")
        }
    }

    fun attachedResourceIds(images: JsonArray?): Set<String> {
        if (images == null || images.isEmpty) return emptySet()
        return images.mapNotNull { element ->
            if (element?.isJsonObject != true) return@mapNotNull null
            GsonUtils.getString("resourceId", element.asJsonObject).takeIf { it.isNotEmpty() }
        }.toSet()
    }

    fun stripAttachedImages(message: String?, resourceIds: Set<String>): String {
        val text = message.orEmpty()
        if (resourceIds.isEmpty() || !text.contains("![")) return text
        var result = text
        for (id in resourceIds) {
            val pattern = Regex("!\\[[^]]*]\\(\\s*resources/${Regex.escape(id)}/[^)]*\\)")
            result = result.replace(pattern, "")
        }
        return if (result == text) text else result.trimEnd()
    }

    fun inlineResourcePaths(message: String?): List<String> {
        val text = message ?: return emptyList()
        if (!text.contains("![")) return emptyList()
        return inlineResourceImage.findAll(text).map { it.groupValues[1] }.toList()
    }

    fun referencedResourceIds(list: List<News?>): Set<String> {
        val ids = mutableSetOf<String>()
        list.forEach { news ->
            news ?: return@forEach
            ids += attachedResourceIds(news.imagesArray)
            inlineResourcePaths(news.message).mapNotNullTo(ids) { path ->
                path.substringBefore('/').takeIf { it.isNotEmpty() }
            }
        }
        return ids
    }

    fun stripUnavailableImages(message: String, isAvailable: (String) -> Boolean): String {
        if (!message.contains("![")) return message
        val result = inlineResourceImage.replace(message) { match ->
            if (isAvailable(match.groupValues[1])) match.value else ""
        }
        return if (result == message) message else result.trim()
    }
    
    fun refreshOnImageDownloads(owner: LifecycleOwner, events: Flow<Intent>, adapter: () -> VoicesAdapter?): Job =
        owner.lifecycleScope.launch {
            events.collect { intent ->
                val resourceId = downloadedResourceId(intent) ?: return@collect
                adapter()?.onResourceDownloaded(resourceId)
            }
        }

    private fun downloadedResourceId(intent: Intent): String? {
        if (intent.action != DownloadService.MESSAGE_PROGRESS) return null
        val download = IntentCompat.getParcelableExtra(intent, "download", Download::class.java)
        if (download == null || download.failed || download.progress != 100) return null
        return resourceIdFromDownloadUrl(download.fileUrl)
    }

    fun resourceIdFromDownloadUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        val segments = url.substringBefore('?').substringBefore('#').split('/')
        val index = segments.lastIndexOf("resources")
        return segments.getOrNull(index + 1)?.takeIf { index >= 0 && it.isNotEmpty() }
    }
}
