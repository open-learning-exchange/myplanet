package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import org.ole.planet.myplanet.utils.toGson

// App-side Gson helpers for TagEntity, kept out of the Room entity.

fun TagEntity.Companion.getTagsArray(list: List<TagEntity>): JsonArray = buildJsonArray {
    for (t in list) {
        add(t._id)
    }
}.toGson()
