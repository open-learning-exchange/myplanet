package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.ole.planet.myplanet.data.room.entity.DictionaryEntity

@Dao
interface DictionaryDao {
    @Query("SELECT COUNT(*) FROM dictionary")
    suspend fun count(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DictionaryEntity>)

    @Query("SELECT * FROM dictionary WHERE word = :word COLLATE NOCASE LIMIT 1")
    suspend fun findByWord(word: String): DictionaryEntity?
}
