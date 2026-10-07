package org.ole.planet.myplanet.ui.user

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.serialization.json.JsonArray as KJsonArray
import org.ole.planet.myplanet.model.Achievement.Companion.createReference
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toGsonAsParsed

/** The achievement and reference entries [EditAchievementFragment] edits before they are saved. */
internal object AchievementEntries {
    /** The stored entries being edited, or `null` when there are none to load. */
    fun loaded(stored: KJsonArray?): JsonArray? = stored?.toGsonAsParsed()

    fun entry(description: String, title: String, date: String, link: String, resources: JsonArray?): JsonObject {
        val entry = JsonObject()
        entry.addProperty("description", description)
        entry.addProperty("title", title)
        entry.addProperty("date", date)
        entry.addProperty("link", link)
        entry.add("resources", resources)
        return entry
    }

    fun resources(libraries: List<MyLibrary>): JsonArray {
        val resources = JsonArray()
        for (library in libraries) resources.add(library.serializeResource().toGson())
        return resources
    }

    fun reference(name: String, relationship: String, phone: String, email: String): JsonObject =
        createReference(name, relationship, phone, email).toGson()
}
