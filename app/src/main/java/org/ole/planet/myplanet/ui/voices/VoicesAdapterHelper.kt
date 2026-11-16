package org.ole.planet.myplanet.ui.voices

import android.content.Context
import com.google.gson.JsonArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.TextRevealer
import org.ole.planet.myplanet.utils.Utilities

object VoicesAdapterHelper {
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

    // Attachments are already shown as thumbnails beside the message, so drop their inline
    // copies; an inline image whose file isn't on disk yet renders as an "OBJ" glyph.
    fun stripAttachedImages(message: String?, resourceIds: Set<String>): String {
        val text = message.orEmpty()
        if (resourceIds.isEmpty() || !text.contains("![")) return text
        var result = text
        for (id in resourceIds) {
            val pattern = Regex("!\\[[^\\]]*]\\(\\s*resources/${Regex.escape(id)}/[^)]*\\)")
            result = result.replace(pattern, "")
        }
        return if (result == text) text else result.trimEnd()
    }

    fun resourceIdFromDownloadUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        val segments = url.substringBefore('?').substringBefore('#').split('/')
        val index = segments.lastIndexOf("resources")
        return segments.getOrNull(index + 1)?.takeIf { index >= 0 && it.isNotEmpty() }
    }
}
