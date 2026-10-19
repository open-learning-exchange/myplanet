package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.ole.planet.myplanet.model.ExamQuestion

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
    @Query("SELECT COUNT(*) FROM exam_questions WHERE examId = :examId") suspend fun countByExamId(examId: String): Int
    @Upsert suspend fun upsertAll(items: List<ExamQuestion>)
    @Query("DELETE FROM exam_questions WHERE examId = :examId AND id NOT IN (:keepIds)") suspend fun deleteStaleInternal(examId: String, keepIds: List<String>)

    // Questions removed from an exam on the server; an empty keep list is ignored rather than wiping the exam
    suspend fun deleteStaleQuestions(questions: List<ExamQuestion>) {
        questions.groupBy { it.examId }.forEach { (examId, examQuestions) ->
            if (examId != null) deleteStaleInternal(examId, examQuestions.map { it.id }.distinct())
        }
    }
}