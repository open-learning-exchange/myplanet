package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("UPDATE health_examinations SET isUpdated = 0 WHERE _id IN (:ids)")
    suspend fun markUploaded(ids: List<String>)

    @Transaction
    suspend fun markUploaded(idToRevMap: Map<String, String?>) {
        val (nullRevEntries, nonNullRevEntries) = idToRevMap.entries.partition { it.value == null }
        if (nullRevEntries.isNotEmpty()) {
            nullRevEntries.map { it.key }.chunked(900).forEach { chunk ->
                markUploaded(chunk)
            }
        }
        nonNullRevEntries.forEach { (id, rev) ->
            markUploaded(id, rev)
        }
    }

    @Query("UPDATE health_examinations SET userId = :userId WHERE _id = :id")
    suspend fun updateUserId(id: String, userId: String)

    @Query("SELECT * FROM health_examinations WHERE profileId = :profileId")
    suspend fun getByProfileId(profileId: String): List<HealthExamination>
}
