package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.ole.planet.myplanet.model.ExamQuestion

@Dao
interface QuestionDao {
    @Query("SELECT * FROM exam_questions WHERE id IN (:ids)") suspend fun getByIds(ids: List<String>): List<ExamQuestion>
    @Query("SELECT * FROM exam_questions WHERE examId = :examId") suspend fun getByExamId(examId: String): List<ExamQuestion>
    @Query("SELECT * FROM exam_questions WHERE examId IN (:examIds)") suspend fun getByExamIds(examIds: List<String>): List<ExamQuestion>
    @Query("SELECT COUNT(*) FROM exam_questions WHERE examId = :examId") suspend fun countByExamId(examId: String): Int
    @Upsert suspend fun upsertAll(items: List<ExamQuestion>)
    @Upsert fun upsertAllBlocking(items: List<ExamQuestion>)
}