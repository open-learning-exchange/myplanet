package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.room.dao.DictionaryDao
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils

class DictionaryRepositoryImpl @Inject constructor(
    private val dictionaryDao: DictionaryDao,
    private val dispatcherProvider: DispatcherProvider,
    private val dictionaryAssetDataSource: DictionaryAssetDataSource
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
            if (!dictionaryAssetDataSource.isDictionaryAssetPresent()) {
                return@withContext DictionaryLoad.FileMissing
            }

            seedMutex.withLock {
                if (dictionaryDao.count() > 0) {
                    return@withLock DictionaryLoad.AlreadyPopulated
                }

                try {
                    val data = dictionaryAssetDataSource.readDictionaryAssetText()
                    val json = data?.let { JsonUtils.gson.fromJson(it, JsonArray::class.java) }
                    if (json != null) {
                        val entities = DictionaryMapper.mapJsonArrayToEntities(json)
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
