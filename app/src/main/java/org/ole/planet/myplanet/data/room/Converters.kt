package org.ole.planet.myplanet.data.room

import androidx.room.TypeConverter
import java.util.Date
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ole.planet.myplanet.model.Attachment
import org.ole.planet.myplanet.model.Conversation

/**
 * Room type converters used across the Room schema.
 *
 * Realm modelled multivalued primitive fields with `RealmList<String>`. In Room those become
 * plain `List<String>` columns persisted as a JSON string, so the on-device representation is
 * self-describing and survives the drop-and-resync migration away from Realm.
 *
 * [json] tolerates unknown/missing keys and coerces malformed values to defaults so that rows
 * already written by a prior app version - back when this used Gson - keep decoding correctly;
 * a plain field-matching class with no custom names/serializers round-trips identically either
 * way, so no Room schema version bump is needed for this converter alone.
 */
class Converters {
    @TypeConverter
    fun fromDate(value: Date?): Long? {
        return value?.time
    }

    @TypeConverter
    fun toDate(value: Long?): Date? {
        return value?.let(::Date)
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value.isNullOrBlank()) return null
        return json.decodeFromString<List<String>>(value)
    }

    @TypeConverter
    fun fromConversationList(value: List<Conversation>?): String? {
        return value?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toConversationList(value: String?): List<Conversation>? {
        if (value.isNullOrBlank()) return null
        return json.decodeFromString<List<Conversation>>(value)
    }

    @TypeConverter
    fun fromAttachmentList(value: List<Attachment>?): String? {
        return value?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toAttachmentList(value: String?): List<Attachment>? {
        if (value.isNullOrBlank()) return null
        return json.decodeFromString<List<Attachment>>(value)
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}
