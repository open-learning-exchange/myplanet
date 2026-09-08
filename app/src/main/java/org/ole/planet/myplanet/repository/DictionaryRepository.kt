package org.ole.planet.myplanet.repository

data class DictionaryWord(
    val word: String = "",
    val meaning: String = "",
    val definition: String = "",
    val synonym: String = "",
    val antonym: String = ""
)

sealed interface DictionaryLoad {
    data object FileMissing : DictionaryLoad
    data object AlreadyPopulated : DictionaryLoad
    data object Inserted : DictionaryLoad
    data class Failed(val cause: Throwable?) : DictionaryLoad
}

interface DictionaryRepository {
    suspend fun count(): Long
    suspend fun findByWord(word: String): DictionaryWord?
    suspend fun insertDictionaryData(): DictionaryLoad
}
