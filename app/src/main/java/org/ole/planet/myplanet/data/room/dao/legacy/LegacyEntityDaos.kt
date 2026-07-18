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
    @Query("SELECT * FROM course_steps WHERE courseId IN (:courseIds)") suspend fun getByCourseIds(courseIds: List<String>): List<RoomCourseStepEntity>
    @Query("SELECT * FROM course_steps WHERE id = :id LIMIT 1") suspend fun getById(id: String): RoomCourseStepEntity?
    @Upsert suspend fun upsertAll(items: List<RoomCourseStepEntity>)
    @Upsert fun upsertAllBlocking(items: List<RoomCourseStepEntity>)
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams WHERE id IN (:ids)") suspend fun getByIds(ids: List<String>): List<RoomExamEntity>
    @Query("SELECT * FROM exams WHERE id = :id LIMIT 1") suspend fun getById(id: String): RoomExamEntity?
    @Query("SELECT * FROM exams WHERE stepId = :stepId LIMIT 1") suspend fun getFirstByStepId(stepId: String): RoomExamEntity?
    @Query("SELECT * FROM exams WHERE courseId = :courseId") suspend fun getByCourseId(courseId: String): List<RoomExamEntity>
    @Query("SELECT * FROM exams WHERE courseId IN (:courseIds)") suspend fun getByCourseIds(courseIds: List<String>): List<RoomExamEntity>
    @Query("SELECT * FROM exams WHERE courseId = :courseId AND type = :type") suspend fun getByCourseIdAndType(courseId: String, type: String): List<RoomExamEntity>
    @Query("SELECT * FROM exams WHERE stepId = :stepId") suspend fun getByStepId(stepId: String): List<RoomExamEntity>
    @Query("SELECT * FROM exams WHERE stepId IN (:stepIds)") suspend fun getByStepIds(stepIds: List<String>): List<RoomExamEntity>
    @Query("SELECT * FROM exams WHERE stepId = :stepId AND type = :type") suspend fun getByStepIdAndType(stepId: String, type: String): List<RoomExamEntity>
    @Query("SELECT * FROM exams WHERE sourceSurveyId IS NOT NULL AND _rev IS NULL") suspend fun getPendingAdoptedSurveys(): List<RoomExamEntity>
    @Upsert suspend fun upsertAll(items: List<RoomExamEntity>)
    @Upsert fun upsertAllBlocking(items: List<RoomExamEntity>)
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM exam_questions WHERE examId = :examId") suspend fun getByExamId(examId: String): List<RoomQuestionEntity>
    @Query("SELECT * FROM exam_questions WHERE examId IN (:examIds)") suspend fun getByExamIds(examIds: List<String>): List<RoomQuestionEntity>
    @Query("SELECT COUNT(*) FROM exam_questions WHERE examId = :examId") suspend fun countByExamId(examId: String): Int
    @Upsert suspend fun upsertAll(items: List<RoomQuestionEntity>)
    @Upsert fun upsertAllBlocking(items: List<RoomQuestionEntity>)
}

@Dao
interface SubmissionDao {
    @Query("SELECT * FROM submissions WHERE id = :id OR _id = :id LIMIT 1") suspend fun getByIdOrRemoteId(id: String): RoomSubmissionEntity?
    @Query("SELECT * FROM submissions WHERE id IN (:ids)") suspend fun getByIds(ids: List<String>): List<RoomSubmissionEntity>
    @Query("SELECT * FROM submissions WHERE userId = :userId") suspend fun getByUserId(userId: String): List<RoomSubmissionEntity>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND status = 'pending' AND type = 'survey'") suspend fun getPendingSurveys(userId: String): List<RoomSubmissionEntity>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND status = 'pending' AND type = 'survey' AND teamId IS NULL") suspend fun getUniquePendingSurveyCandidates(userId: String): List<RoomSubmissionEntity>
    @Query("SELECT COUNT(*) FROM submissions WHERE (isUpdated = 1 OR _id = '')") suspend fun countPendingOfflineSubmissions(): Int
    @Query("SELECT COUNT(*) FROM submissions WHERE LOWER(status) = 'pending' AND id IN (SELECT submissionId FROM answers WHERE submissionId IS NOT NULL)") suspend fun countPendingExamResults(): Int
    @Query("SELECT COUNT(*) FROM submissions WHERE userId = :userId AND parentId = :parentId AND type = :type") suspend fun countByUserParentAndType(userId: String?, parentId: String, type: String): Int
    @Query("SELECT COUNT(*) FROM submissions WHERE userId = :userId AND parentId LIKE '%' || :examId || '%' AND status != 'pending'") suspend fun countCompletedByUserAndExamId(userId: String?, examId: String): Int
    @Query("SELECT * FROM submissions WHERE parentId = :parentId AND userId = :userId AND (:status IS NULL OR status = :status) ORDER BY startTime DESC") suspend fun getByParentUserAndStatus(parentId: String?, userId: String?, status: String?): List<RoomSubmissionEntity>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND parentId = :parentId AND status = 'pending' ORDER BY lastUpdateTime DESC LIMIT 1") suspend fun getLatestPendingByUserAndParent(userId: String?, parentId: String): RoomSubmissionEntity?
    @Query("SELECT * FROM submissions WHERE parentId LIKE '%' || :parentIdFragment || '%' LIMIT 1") suspend fun getFirstByParentIdContaining(parentIdFragment: String): RoomSubmissionEntity?
    @Query("UPDATE submissions SET user = :userJson, status = 'complete', isUpdated = 1 WHERE id = :id") suspend fun markComplete(id: String, userJson: String): Int
    @Query("DELETE FROM submissions WHERE parentId = :parentId AND userId = :userId") suspend fun deleteByParentAndUser(parentId: String, userId: String?): Int
    @Query("SELECT * FROM submissions WHERE type = 'exam' AND parentId IS NOT NULL AND userId IS NOT NULL AND (_id IS NULL OR _id = '')") suspend fun getPendingExamResults(): List<RoomSubmissionEntity>
    @Query("SELECT * FROM submissions WHERE status = 'complete' AND (isUpdated = 1 OR _id = '')") suspend fun getPendingSubmissions(): List<RoomSubmissionEntity>
    @Upsert suspend fun upsertAll(items: List<RoomSubmissionEntity>)
    @Upsert fun upsertAllBlocking(items: List<RoomSubmissionEntity>)
    @Query("UPDATE submissions SET _id = :remoteId, _rev = :remoteRev, isUpdated = 0 WHERE id = :localId") suspend fun markUploaded(localId: String, remoteId: String?, remoteRev: String?): Int
}

@Dao
interface AnswerDao {
    @Query("SELECT * FROM answers WHERE submissionId = :submissionId") suspend fun getBySubmissionId(submissionId: String): List<RoomAnswerEntity>
    @Query("SELECT * FROM answers WHERE submissionId IN (:submissionIds)") suspend fun getBySubmissionIds(submissionIds: List<String>): List<RoomAnswerEntity>
    @Query("DELETE FROM answers WHERE submissionId IN (:submissionIds)") suspend fun deleteBySubmissionIds(submissionIds: List<String>): Int
    @Upsert suspend fun upsertAll(items: List<RoomAnswerEntity>)
    @Upsert fun upsertAllBlocking(items: List<RoomAnswerEntity>)
}

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams WHERE _id = :teamId OR teamId = :teamId LIMIT 1") suspend fun getByTeamId(teamId: String): RoomTeamEntity?
    @Query("SELECT * FROM teams WHERE userId = :userId") suspend fun getByUserId(userId: String): List<RoomTeamEntity>
    @Upsert suspend fun upsertAll(items: List<RoomTeamEntity>)
}
