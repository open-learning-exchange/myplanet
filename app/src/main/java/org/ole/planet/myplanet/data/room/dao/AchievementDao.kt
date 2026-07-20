package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.ole.planet.myplanet.model.RealmAchievement

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements WHERE _id = :id LIMIT 1")
    suspend fun getById(id: String): RealmAchievement?

    @Upsert
    suspend fun upsert(achievement: RealmAchievement)

    @Upsert
    suspend fun upsertAll(achievements: List<RealmAchievement>)

    @Query("SELECT * FROM achievements WHERE _id NOT LIKE 'guest%' AND isUpdated = 1")
    suspend fun getPendingUploads(): List<RealmAchievement>

    @Query("UPDATE achievements SET _rev = COALESCE(:rev, _rev), isUpdated = 0 WHERE _id = :id")
    suspend fun markUploaded(id: String, rev: String?)
}
