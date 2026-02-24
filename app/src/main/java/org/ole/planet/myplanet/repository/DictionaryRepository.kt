package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import org.ole.planet.myplanet.model.RealmDictionary

interface DictionaryRepository {
    suspend fun getDictionaryCount(): Long
    suspend fun isDictionaryEmpty(): Boolean
    suspend fun importDictionary(jsonArray: JsonArray)
    suspend fun searchWord(term: String): RealmDictionary?
}
