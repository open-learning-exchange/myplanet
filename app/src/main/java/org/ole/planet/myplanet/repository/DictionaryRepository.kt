package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.data.room.entity.DictionaryEntity

sealed interface DictionaryLoad {
    data object FileMissing : DictionaryLoad
    data object AlreadyPopulated : DictionaryLoad
    data object Inserted : DictionaryLoad
    data class Failed(val cause: Throwable?) : DictionaryLoad
}

interface DictionaryRepository {
    suspend fun count(): Long
    suspend fun findByWord(word: String): DictionaryEntity?
    suspend fun insertDictionaryData(): DictionaryLoad
}
