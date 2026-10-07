package org.ole.planet.myplanet.ui.voices

import com.google.gson.JsonObject
import org.ole.planet.myplanet.utils.GsonUtils

/** The `{"imageUrl":...,"fileName":...}` texts a post keeps its attached images as (`News.imageUrls`). */
internal object VoicesImages {
    /** Stored text: Gson().toJson's bytes. */
    fun entry(path: String, fileName: String): String {
        val jsonObject = JsonObject()
        jsonObject.addProperty("imageUrl", path)
        jsonObject.addProperty("fileName", fileName)
        return GsonUtils.gson.toJson(jsonObject)
    }

    /** The entry's `imageUrl`, or `""`; throws on text that is not a JSON object. */
    fun imageUrl(entry: String): String =
        GsonUtils.getString("imageUrl", GsonUtils.gson.fromJson(entry, JsonObject::class.java))
}
