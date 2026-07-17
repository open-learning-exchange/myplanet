package org.ole.planet.myplanet.data.room

import androidx.room.TypeConverter
import com.google.gson.reflect.TypeToken
import org.ole.planet.myplanet.utils.JsonUtils

/**
 * Room type converters used across the Room schema.
 *
 * Realm modelled multi-valued primitive fields with `RealmList<String>`. In Room those become
 * plain `List<String>` columns persisted as a JSON string, so the on-device representation is
 * self-describing and survives the drop-and-resync migration away from Realm.
 */
class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { JsonUtils.gson.toJson(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value.isNullOrBlank()) return null
        return JsonUtils.gson.fromJson(value, object : TypeToken<List<String>>() {}.type)
    }
}
