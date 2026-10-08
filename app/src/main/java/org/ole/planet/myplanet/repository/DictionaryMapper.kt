package org.ole.planet.myplanet.repository

import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.ole.planet.myplanet.data.room.entity.DictionaryEntity

object DictionaryMapper {
    fun mapJsonArrayToEntities(json: JsonArray): List<DictionaryEntity> {
        return json.map { js ->
            val doc = js.jsonObject
            DictionaryEntity(
                id = UUID.randomUUID().toString(),
                code = doc.str("code"),
                language = doc.str("language"),
                advanceCode = doc.str("advance_code"),
                word = doc.str("word"),
                meaning = doc.str("meaning"),
                definition = doc.str("definition"),
                synonym = doc.str("synonym"),
                antonym = doc.str("antonoym")
            )
        }
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
}
