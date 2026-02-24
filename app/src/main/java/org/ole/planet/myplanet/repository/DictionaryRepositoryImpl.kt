package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import io.realm.Case
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmDictionary
import org.ole.planet.myplanet.utils.JsonUtils

class DictionaryRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), DictionaryRepository {

    override suspend fun getDictionaryCount(): Long {
        return count(RealmDictionary::class.java)
    }

    override suspend fun isDictionaryEmpty(): Boolean {
        return count(RealmDictionary::class.java) == 0L
    }

    override suspend fun importDictionary(jsonArray: JsonArray) {
        executeTransaction { realm ->
            jsonArray.forEach { js ->
                val doc = js.asJsonObject
                val dict = realm.createObject(
                    RealmDictionary::class.java, UUID.randomUUID().toString()
                )
                dict.code = JsonUtils.getString("code", doc)
                dict.language = JsonUtils.getString("language", doc)
                dict.advanceCode = JsonUtils.getString("advance_code", doc)
                dict.word = JsonUtils.getString("word", doc)
                dict.meaning = JsonUtils.getString("meaning", doc)
                dict.definition = JsonUtils.getString("definition", doc)
                dict.synonym = JsonUtils.getString("synonym", doc)
                dict.antonym = JsonUtils.getString("antonoym", doc)
            }
        }
    }

    override suspend fun searchWord(term: String): RealmDictionary? {
        return withRealm { realm ->
            realm.where(RealmDictionary::class.java)
                .equalTo("word", term, Case.INSENSITIVE)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }
}
