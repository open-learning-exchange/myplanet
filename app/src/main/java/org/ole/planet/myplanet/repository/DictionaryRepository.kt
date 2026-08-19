package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.data.room.entity.DictionaryEntity

interface DictionaryRepository {
    suspend fun count(): Long
    suspend fun findByWord(word: String): DictionaryEntity?
    suspend fun insertDictionaryData(): Boolean
}
