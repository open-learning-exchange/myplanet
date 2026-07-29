package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.ole.planet.myplanet.model.Achievement

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements WHERE _id = :id LIMIT 1")
    suspend fun getById(id: String): Achievement?

    @Upsert
    suspend fun upsert(achievement: Achievement)

    @Upsert
    suspend fun upsertAll(achievements: List<Achievement>)

    @Query("SELECT * FROM achievements WHERE _id NOT LIKE 'guest%' AND isUpdated = 1")
    suspend fun getPendingUploads(): List<Achievement>

    @Query("UPDATE achievements SET _rev = COALESCE(:rev, _rev), isUpdated = 0 WHERE _id = :id")
    suspend fun markUploaded(id: String, rev: String?)
}
