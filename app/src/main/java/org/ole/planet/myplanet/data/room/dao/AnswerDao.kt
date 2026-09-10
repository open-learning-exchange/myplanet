package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.ole.planet.myplanet.model.Answer

@Dao
interface AnswerDao {
    @Query("SELECT * FROM answers WHERE submissionId = :submissionId") suspend fun getBySubmissionId(submissionId: String): List<Answer>

    @Query("SELECT * FROM answers WHERE submissionId IN (:submissionIds)")
    suspend fun getBySubmissionIdsInternal(submissionIds: List<String>): List<Answer>

    suspend fun getBySubmissionIds(submissionIds: List<String>): List<Answer> {
        if (submissionIds.isEmpty()) return emptyList()
        return submissionIds.chunked(900).flatMap { getBySubmissionIdsInternal(it) }
    }

    @Query("SELECT * FROM answers WHERE submissionId = :submissionId AND questionId = :questionId LIMIT 1") suspend fun getBySubmissionAndQuestion(submissionId: String, questionId: String?): Answer?

    @Query("DELETE FROM answers WHERE submissionId IN (:submissionIds)")
    suspend fun deleteBySubmissionIdsInternal(submissionIds: List<String>): Int

    suspend fun deleteBySubmissionIds(submissionIds: List<String>): Int {
        if (submissionIds.isEmpty()) return 0
        return submissionIds.chunked(900).sumOf { deleteBySubmissionIdsInternal(it) }
    }

    @Upsert suspend fun upsertAll(items: List<Answer>)
    @Upsert fun upsertAllBlocking(items: List<Answer>)
}
