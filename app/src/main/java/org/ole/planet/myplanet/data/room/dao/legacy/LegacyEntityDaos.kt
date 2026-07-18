package org.ole.planet.myplanet.data.room.dao.legacy

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.ole.planet.myplanet.data.room.entity.legacy.RoomAnswerEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomCourseEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomCourseStepEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomExamEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomQuestionEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomSubmissionEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomTeamEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomUserEntity

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id OR _id = :id LIMIT 1")
    suspend fun getById(id: String): RoomUserEntity?
    @Upsert suspend fun upsertAll(items: List<RoomUserEntity>)
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses") suspend fun getAll(): List<RoomCourseEntity>
    @Query("SELECT * FROM courses WHERE courseId = :courseId OR id = :courseId LIMIT 1") suspend fun getByCourseId(courseId: String): RoomCourseEntity?
    @Upsert suspend fun upsertAll(items: List<RoomCourseEntity>)
    @Upsert fun upsertAllBlocking(items: List<RoomCourseEntity>)
}

@Dao
interface CourseStepDao {
    @Query("SELECT * FROM course_steps WHERE courseId = :courseId") suspend fun getByCourseId(courseId: String): List<RoomCourseStepEntity>
    @Upsert suspend fun upsertAll(items: List<RoomCourseStepEntity>)
    @Upsert fun upsertAllBlocking(items: List<RoomCourseStepEntity>)
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams WHERE courseId = :courseId") suspend fun getByCourseId(courseId: String): List<RoomExamEntity>
    @Query("SELECT * FROM exams WHERE stepId = :stepId") suspend fun getByStepId(stepId: String): List<RoomExamEntity>
    @Query("SELECT * FROM exams WHERE sourceSurveyId IS NOT NULL AND _rev IS NULL") suspend fun getPendingAdoptedSurveys(): List<RoomExamEntity>
    @Upsert suspend fun upsertAll(items: List<RoomExamEntity>)
    @Upsert fun upsertAllBlocking(items: List<RoomExamEntity>)
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM exam_questions WHERE examId = :examId") suspend fun getByExamId(examId: String): List<RoomQuestionEntity>
    @Upsert suspend fun upsertAll(items: List<RoomQuestionEntity>)
    @Upsert fun upsertAllBlocking(items: List<RoomQuestionEntity>)
}

@Dao
interface SubmissionDao {
    @Query("SELECT * FROM submissions WHERE type = 'exam' AND parentId IS NOT NULL AND userId IS NOT NULL AND (_id IS NULL OR _id = '')") suspend fun getPendingExamResults(): List<RoomSubmissionEntity>
    @Query("SELECT * FROM submissions WHERE status = 'complete' AND (isUpdated = 1 OR _id = '')") suspend fun getPendingSubmissions(): List<RoomSubmissionEntity>
    @Upsert suspend fun upsertAll(items: List<RoomSubmissionEntity>)
    @Query("UPDATE submissions SET _id = :remoteId, _rev = :remoteRev, isUpdated = 0 WHERE id = :localId") suspend fun markUploaded(localId: String, remoteId: String?, remoteRev: String?): Int
}

@Dao
interface AnswerDao {
    @Query("SELECT * FROM answers WHERE submissionId = :submissionId") suspend fun getBySubmissionId(submissionId: String): List<RoomAnswerEntity>
    @Upsert suspend fun upsertAll(items: List<RoomAnswerEntity>)
}

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams WHERE _id = :teamId OR teamId = :teamId LIMIT 1") suspend fun getByTeamId(teamId: String): RoomTeamEntity?
    @Query("SELECT * FROM teams WHERE userId = :userId") suspend fun getByUserId(userId: String): List<RoomTeamEntity>
    @Upsert suspend fun upsertAll(items: List<RoomTeamEntity>)
}
