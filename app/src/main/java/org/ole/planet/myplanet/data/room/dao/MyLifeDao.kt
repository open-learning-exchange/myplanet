package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.ole.planet.myplanet.model.RealmMyLife

@Dao
interface MyLifeDao {
    // `IS` (not `=`) so a null userId matches null rows, mirroring Realm's equalTo(null) semantics.
    @Query("SELECT * FROM my_life WHERE userId IS :userId ORDER BY weight")
    suspend fun getByUserId(userId: String?): List<RealmMyLife>

    @Query("SELECT * FROM my_life WHERE userId IS :userId AND isVisible = 1 ORDER BY weight")
    suspend fun getVisibleByUserId(userId: String?): List<RealmMyLife>

    @Query("SELECT COUNT(*) FROM my_life WHERE userId IS :userId")
    suspend fun countByUserId(userId: String?): Int

    @Query("SELECT * FROM my_life WHERE _id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<RealmMyLife>

    @Query("UPDATE my_life SET isVisible = :isVisible WHERE _id = :id")
    suspend fun updateVisibility(id: String, isVisible: Boolean)

    @Update
    suspend fun update(items: List<RealmMyLife>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RealmMyLife>)
}
