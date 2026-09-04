package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonArray
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val seedMutex = Mutex()

    override suspend fun count(): Long {
        return dictionaryDao.count()
    }

    override suspend fun findByWord(word: String): DictionaryWord? {
        val entity = dictionaryDao.findByWord(word) ?: return null
        return DictionaryWord(
            word = entity.word,
            meaning = entity.meaning,
            definition = entity.definition,
            synonym = entity.synonym,
            antonym = entity.antonym
        )
    }

    override suspend fun insertDictionaryData(): DictionaryLoad {
        return withContext(dispatcherProvider.io) {
            if (!FileUtils.checkFileExist(context, Constants.DICTIONARY_URL)) {
                return@withContext DictionaryLoad.FileMissing
            }

            seedMutex.withLock {
                if (dictionaryDao.count() > 0) {
                    return@withLock DictionaryLoad.AlreadyPopulated
                }

                try {
                    val data = FileUtils.getStringFromFile(
                        FileUtils.getSDPathFromUrl(context, Constants.DICTIONARY_URL)
                    )
                    val json = JsonUtils.gson.fromJson(data, JsonArray::class.java)
                    if (json != null) {
                        val entities = json.map { js ->
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
                        dictionaryDao.insertAll(entities)
                        DictionaryLoad.Inserted
                    } else {
                        DictionaryLoad.Failed(null)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    DictionaryLoad.Failed(e)
                }
            }
        }
    }
}
