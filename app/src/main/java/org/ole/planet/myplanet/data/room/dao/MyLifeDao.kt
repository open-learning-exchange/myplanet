package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.ole.planet.myplanet.model.MyLife

@Dao
interface MyLifeDao {
    @Query("SELECT * FROM my_life WHERE (:userId IS NULL OR userId IS NULL OR userId = :userId) ORDER BY weight")
    suspend fun getByUserId(userId: String?): List<MyLife>

    @Query("SELECT * FROM my_life WHERE (:userId IS NULL OR userId IS NULL OR userId = :userId) AND isVisible = 1 ORDER BY weight")
    suspend fun getVisibleByUserId(userId: String?): List<MyLife>

    @Query("SELECT COUNT(*) FROM my_life WHERE (:userId IS NULL OR userId IS NULL OR userId = :userId)")
    suspend fun countByUserId(userId: String?): Int

    @Query("SELECT * FROM my_life WHERE _id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<MyLife>

    @Query("UPDATE my_life SET isVisible = :isVisible WHERE _id = :id OR imageId = :id OR title = :id")
    suspend fun updateVisibility(id: String, isVisible: Boolean)

    @Update
    suspend fun update(items: List<MyLife>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MyLife>)
}
