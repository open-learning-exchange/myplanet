package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import java.util.UUID
import org.ole.planet.myplanet.data.room.entity.DictionaryEntity
import org.ole.planet.myplanet.utils.GsonUtils

object DictionaryMapper {
    fun mapJsonArrayToEntities(json: JsonArray): List<DictionaryEntity> {
        return json.map { js ->
            val doc = js.asJsonObject
            DictionaryEntity(
                id = UUID.randomUUID().toString(),
                code = GsonUtils.getString("code", doc),
                language = GsonUtils.getString("language", doc),
                advanceCode = GsonUtils.getString("advance_code", doc),
                word = GsonUtils.getString("word", doc),
                meaning = GsonUtils.getString("meaning", doc),
                definition = GsonUtils.getString("definition", doc),
                synonym = GsonUtils.getString("synonym", doc),
                antonym = GsonUtils.getString("antonoym", doc)
            )
        }
    }
}
