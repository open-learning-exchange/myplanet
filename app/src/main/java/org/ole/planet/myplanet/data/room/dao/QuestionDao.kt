package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.ole.planet.myplanet.model.ExamQuestion

data class ExamQuestionCount(val examId: String?, val count: Int)

@Dao
interface QuestionDao {
    @Query("SELECT * FROM exam_questions WHERE id IN (:ids)") suspend fun getByIdsInternal(ids: List<String>): List<ExamQuestion>

    suspend fun getByIds(ids: List<String>): List<ExamQuestion> {
        if (ids.isEmpty()) return emptyList()
        return ids.distinct().chunked(900).flatMap { getByIdsInternal(it) }
    }

    @Query("SELECT * FROM exam_questions WHERE examId = :examId") suspend fun getByExamId(examId: String): List<ExamQuestion>
    @Query("SELECT * FROM exam_questions WHERE examId IN (:examIds)")
    suspend fun getByExamIdsInternal(examIds: List<String>): List<ExamQuestion>

    suspend fun getByExamIds(examIds: List<String>): List<ExamQuestion> {
        if (examIds.isEmpty()) return emptyList()
        return examIds.distinct().chunked(900).flatMap { getByExamIdsInternal(it) }
    }

    @Query("SELECT examId, COUNT(*) AS count FROM exam_questions WHERE examId IN (:examIds) GROUP BY examId")
    suspend fun countByExamIdsInternal(examIds: List<String>): List<ExamQuestionCount>

    suspend fun countByExamIds(examIds: List<String>): Map<String, Int> {
        if (examIds.isEmpty()) return emptyMap()
        return examIds.distinct().chunked(900).flatMap { countByExamIdsInternal(it) }
            .mapNotNull { projection ->
                val examId = projection.examId ?: return@mapNotNull null
                examId to projection.count
            }.toMap()
    }

    @Query("SELECT COUNT(*) FROM exam_questions WHERE examId = :examId") suspend fun countByExamId(examId: String): Int
    @Upsert suspend fun upsertAll(items: List<ExamQuestion>)
    @Query("SELECT id FROM exam_questions WHERE examId = :examId") suspend fun getIdsByExamId(examId: String): List<String>
    @Query("DELETE FROM exam_questions WHERE examId = :examId AND id IN (:ids)") suspend fun deleteByIdsInternal(examId: String, ids: List<String>)

    @androidx.room.Transaction
    suspend fun deleteStaleQuestions(questions: List<ExamQuestion>) {
        questions.groupBy { it.examId }.forEach { (examId, examQuestions) ->
            if (examId != null) {
                val storedIds = getIdsByExamId(examId)
                val keepIds = examQuestions.map { it.id }.toSet()
                val stale = storedIds.toSet() - keepIds
                if (stale.isNotEmpty()) {
                    stale.chunked(900).forEach { deleteByIdsInternal(examId, it) }
                }
            }
        }
    }
}
