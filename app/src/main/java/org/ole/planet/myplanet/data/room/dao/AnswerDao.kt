package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.ole.planet.myplanet.model.Answer

@Dao
interface AnswerDao {
    @Query("SELECT * FROM answers WHERE submissionId = :submissionId") suspend fun getBySubmissionId(submissionId: String): List<Answer>
    @Query("SELECT * FROM answers WHERE submissionId IN (:submissionIds)") suspend fun getBySubmissionIds(submissionIds: List<String>): List<Answer>
    @Query("SELECT * FROM answers WHERE submissionId = :submissionId AND questionId = :questionId LIMIT 1") suspend fun getBySubmissionAndQuestion(submissionId: String, questionId: String?): Answer?
    @Query("DELETE FROM answers WHERE submissionId IN (:submissionIds)") suspend fun deleteBySubmissionIds(submissionIds: List<String>): Int
    @Upsert suspend fun upsertAll(items: List<Answer>)
    @Upsert fun upsertAllBlocking(items: List<Answer>)
}