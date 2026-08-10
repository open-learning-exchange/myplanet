package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.StepExam

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams WHERE id IN (:ids)") suspend fun getByIds(ids: List<String>): List<StepExam>
    @Query("SELECT * FROM exams WHERE id = :id LIMIT 1") suspend fun getById(id: String): StepExam?
    @Query("SELECT * FROM exams WHERE stepId = :stepId LIMIT 1") suspend fun getFirstByStepId(stepId: String): StepExam?
    @Query("SELECT * FROM exams WHERE courseId = :courseId") suspend fun getByCourseId(courseId: String): List<StepExam>
    @Query("SELECT * FROM exams WHERE courseId IN (:courseIds)") suspend fun getByCourseIds(courseIds: List<String>): List<StepExam>
    @Query("SELECT * FROM exams WHERE courseId = :courseId AND type = :type") suspend fun getByCourseIdAndType(courseId: String, type: String): List<StepExam>
    @Query("SELECT * FROM exams WHERE stepId = :stepId") suspend fun getByStepId(stepId: String): List<StepExam>
    @Query("SELECT * FROM exams WHERE stepId IN (:stepIds)") suspend fun getByStepIds(stepIds: List<String>): List<StepExam>
    @Query("SELECT * FROM exams WHERE stepId = :stepId AND type = :type") suspend fun getByStepIdAndType(stepId: String, type: String): List<StepExam>
    @Query("SELECT * FROM exams WHERE sourceSurveyId IS NOT NULL AND _rev IS NULL") suspend fun getPendingAdoptedSurveys(): List<StepExam>
    @Query("SELECT * FROM exams") suspend fun getAll(): List<StepExam>
    @Query("SELECT * FROM exams") fun observeAll(): Flow<List<StepExam>>
    @Query("SELECT * FROM exams WHERE type = :type") suspend fun getByType(type: String): List<StepExam>
    @Query("SELECT * FROM exams WHERE type = :type") fun observeByType(type: String): Flow<List<StepExam>>
    @Query("SELECT * FROM exams WHERE teamId = :teamId") suspend fun getByTeamId(teamId: String): List<StepExam>
    @Query("SELECT * FROM exams WHERE teamId = :teamId AND type = :type") suspend fun getByTeamIdAndType(teamId: String, type: String): List<StepExam>
    @Query("DELETE FROM exams WHERE id = :id") suspend fun deleteById(id: String): Int
    @Upsert suspend fun upsert(item: StepExam)
    @Upsert suspend fun upsertAll(items: List<StepExam>)
    @Upsert fun upsertAllBlocking(items: List<StepExam>)
}