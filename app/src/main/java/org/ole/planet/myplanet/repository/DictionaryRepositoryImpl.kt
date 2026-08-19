package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonArray
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.room.dao.DictionaryDao
import org.ole.planet.myplanet.data.room.entity.DictionaryEntity
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils

class DictionaryRepositoryImpl @Inject constructor(
    private val dictionaryDao: DictionaryDao,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context
) : DictionaryRepository {

    override suspend fun count(): Long {
        return withContext(dispatcherProvider.io) {
            dictionaryDao.count()
        }
    }

    override suspend fun findByWord(word: String): DictionaryEntity? {
        return withContext(dispatcherProvider.io) {
            dictionaryDao.findByWord(word)
        }
    }

    override suspend fun insertDictionaryData(): Boolean {
        if (!FileUtils.checkFileExist(context, Constants.DICTIONARY_URL)) {
            return false
        }

        if (count() > 0) {
            return true
        }

        val json = try {
            val data = withContext(dispatcherProvider.io) {
                FileUtils.getStringFromFile(
                    FileUtils.getSDPathFromUrl(context, Constants.DICTIONARY_URL)
                )
            }
            JsonUtils.gson.fromJson(data, JsonArray::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        json?.let { jsonArray ->
            val entities = jsonArray.map { js ->
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
            withContext(dispatcherProvider.io) {
                dictionaryDao.insertAll(entities)
            }
        }

        return true
    }
}
