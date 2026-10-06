package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.toGson

// App-side Gson helpers for MyLibrary, kept out of the Room entity.

fun MyLibrary.serializeResource(): JsonObject = buildJsonObject {
    put("_id", _id)
    put("_rev", _rev)
    put("need_optimization", needsOptimization)
    put("resourceFor", resourceFor.toJsonArray())
    put("publisher", publisher)
    put("linkToLicense", linkToLicense)
    put("addedBy", addedBy)
    put("uploadDate", uploadDate)
    put("openWith", openWith)
    put("subject", subject.toJsonArray())
    put("kind", kind)
    put("medium", medium)
    put("language", language)
    put("author", author)
    put("sum", sum)
    put("createdDate", uploadDate)
    put("level", level.toJsonArray())
    put("languages", languages.toJsonArray())
    put("tag", tag.toJsonArray())
    put("timesRated", timesRated)
    put("year", year)
    put("title", title)
    put("averageRating", averageRating)
    put("filename", filename)
    put("mediaType", mediaType)
    put("description", description)
    put("_attachments", buildJsonObject {
        resourceLocalAddress?.let { addr ->
            put(addr, buildJsonObject { })
        }
    })
}.toGson()

private fun List<String>?.toJsonArray() = buildJsonArray {
    this@toJsonArray?.forEach { add(it) }
}
