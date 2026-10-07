package org.ole.planet.myplanet.ui.voices

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.KJson

/** The `{"imageUrl":...,"fileName":...}` texts a post keeps its attached images as (`News.imageUrls`). */
internal object VoicesImages {
    /** Stored text: Gson().toJson's bytes, which [KJson.gsonToJson] reproduces. */
    fun entry(path: String, fileName: String): String = KJson.gsonToJson(
        buildJsonObject {
            put("imageUrl", path)
            put("fileName", fileName)
        }
    )

    /** The entry's `imageUrl`, or `""`; throws on text that is not a JSON object. */
    fun imageUrl(entry: String): String = KJson.getString("imageUrl", KJson.parseObjectOrNull(entry))
}
