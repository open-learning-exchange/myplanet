package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.ole.planet.myplanet.model.HealthExamination

@Dao
interface HealthExaminationDao {
    @Query("SELECT * FROM health_examinations WHERE _id = :id OR userId = :id LIMIT 1")
    suspend fun getByIdOrUserId(id: String): HealthExamination?

    @Query("SELECT * FROM health_examinations WHERE _id = :id LIMIT 1")
    suspend fun getById(id: String): HealthExamination?

    @Query("SELECT * FROM health_examinations WHERE isUpdated = 1 AND userId != ''")
    suspend fun getUpdated(): List<HealthExamination>

    @Query("SELECT * FROM health_examinations WHERE isUpdated = 1 AND userId = :userId")
    suspend fun getUpdatedForUser(userId: String): List<HealthExamination>

    @Upsert
    suspend fun upsert(examination: HealthExamination)

    @Upsert
    suspend fun upsertAll(examinations: List<HealthExamination>)

    @Query("UPDATE health_examinations SET _rev = :rev, isUpdated = 0 WHERE _id = :id")
    suspend fun markUploaded(id: String, rev: String?)

    @Query("UPDATE health_examinations SET userId = :userId WHERE _id = :id")
    suspend fun updateUserId(id: String, userId: String)

    @Query("SELECT * FROM health_examinations WHERE profileId = :profileId")
    suspend fun getByProfileId(profileId: String): List<HealthExamination>
}
