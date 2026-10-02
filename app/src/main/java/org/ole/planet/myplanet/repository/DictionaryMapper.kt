package org.ole.planet.myplanet.repository

import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.ole.planet.myplanet.data.room.entity.DictionaryEntity

object DictionaryMapper {
    fun mapJsonArrayToEntities(json: JsonArray): List<DictionaryEntity> {
        val occurrences = mutableMapOf<String, Int>()
        return json.map { js ->
            val doc = js.jsonObject
            val code = doc.str("code")
            val language = doc.str("language")
            val advanceCode = doc.str("advance_code")
            val word = doc.str("word")
            val meaning = doc.str("meaning")
            val definition = doc.str("definition")
            val synonym = doc.str("synonym")
            val antonym = doc.str("antonoym")

            val key = listOf(code, language, advanceCode, word, meaning, definition, synonym, antonym)
                .joinToString("\u0000")
            val n = occurrences.getOrDefault(key, 0)
            occurrences[key] = n + 1

            val id = UUID.nameUUIDFromBytes("$key#$n".toByteArray(Charsets.UTF_8)).toString()

            DictionaryEntity(
                id = id,
                code = code,
                language = language,
                advanceCode = advanceCode,
                word = word,
                meaning = meaning,
                definition = definition,
                synonym = synonym,
                antonym = antonym
            )
        }
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
}
