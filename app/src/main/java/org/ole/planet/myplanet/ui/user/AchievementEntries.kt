package org.ole.planet.myplanet.ui.user

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.model.Achievement.Companion.createReference
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.utils.withGsonBridgedNumbers

/**
 * The achievement and reference entries [EditAchievementFragment] edits before they are saved.
 * Stored entries keep their numbers as written; entries built here hold numbers the way the
 * Gson tree they replace held them ([withGsonBridgedNumbers]), so the saved documents are
 * unchanged.
 */
internal object AchievementEntries {
    /** The stored entries being edited, or `null` when there are none to load. */
    fun loaded(stored: JsonArray?): MutableList<JsonElement>? = stored?.toMutableList()

    fun entry(description: String, title: String, date: String, link: String, resources: JsonArray?): JsonObject = buildJsonObject {
        put("description", description)
        put("title", title)
        put("date", date)
        put("link", link)
        put("resources", resources ?: JsonNull)
    }

    fun resources(libraries: List<MyLibrary>): JsonArray =
        JsonArray(libraries.map { it.serializeResource().withGsonBridgedNumbers() })

    fun reference(name: String, relationship: String, phone: String, email: String): JsonObject =
        createReference(name, relationship, phone, email).withGsonBridgedNumbers()
}
