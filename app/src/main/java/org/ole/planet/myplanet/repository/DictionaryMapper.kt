package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import java.util.UUID
import org.ole.planet.myplanet.data.room.entity.DictionaryEntity
import org.ole.planet.myplanet.utils.JsonUtils

object DictionaryMapper {
    fun mapJsonArrayToEntities(json: JsonArray): List<DictionaryEntity> {
        return json.map { js ->
            val doc = js.asJsonObject
            DictionaryEntity(
                id = UUID.randomUUID().toString(),
                code = JsonUtils.getString("code", doc),
                language = JsonUtils.getString("language", doc),
                advanceCode = JsonUtils.getString("advance_code", doc),
                word = JsonUtils.getString("word", doc),
                meaning = JsonUtils.getString("meaning", doc),
                definition = JsonUtils.getString("definition", doc),
                synonym = JsonUtils.getString("synonym", doc),
                antonym = JsonUtils.getString("antonoym", doc)
            )
        }
    }
}
