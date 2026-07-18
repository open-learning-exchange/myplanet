package org.ole.planet.myplanet.data.room.dao.legacy

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
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
    @Query("SELECT * FROM users") suspend fun getAll(): List<RoomUserEntity>
    @Query("SELECT * FROM users WHERE name = :name LIMIT 1") suspend fun getByName(name: String): RoomUserEntity?
    @Query("SELECT * FROM users WHERE name LIKE '%' || :query || '%' OR firstName LIKE '%' || :query || '%' OR lastName LIKE '%' || :query || '%'") suspend fun search(query: String): List<RoomUserEntity>
    @Query("SELECT COUNT(*) FROM users") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM users WHERE planetCode = :planetCode") suspend fun countByPlanetCode(planetCode: String): Int
    @Query("DELETE FROM users WHERE id = :id") suspend fun deleteById(id: String): Int
    @Upsert suspend fun upsert(item: RoomUserEntity)
    @Upsert suspend fun upsertAll(items: List<RoomUserEntity>)
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses") suspend fun getAll(): List<RoomCourseEntity>
    @Query("SELECT * FROM courses WHERE courseId = :courseId OR id = :courseId LIMIT 1") suspend fun getByCourseId(courseId: String): RoomCourseEntity?
    @Query("SELECT * FROM courses WHERE courseId IN (:courseIds)") suspend fun getByCourseIds(courseIds: List<String>): List<RoomCourseEntity>
    @Query("SELECT * FROM courses") fun observeAll(): Flow<List<RoomCourseEntity>>
    @Query("SELECT * FROM courses WHERE courseId = :courseId OR id = :courseId LIMIT 1") fun observeByCourseId(courseId: String): Flow<RoomCourseEntity?>
    @Query("DELETE FROM courses WHERE courseId = :courseId") suspend fun deleteByCourseId(courseId: String): Int
    @Upsert suspend fun upsertAll(items: List<RoomCourseEntity>)
    @Upsert fun upsertAllBlocking(items: List<RoomCourseEntity>)
    @Upsert suspend fun upsert(item: RoomCourseEntity)
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
    @Query("SELECT * FROM exams") suspend fun getAll(): List<RoomExamEntity>
    @Query("SELECT * FROM exams") fun observeAll(): Flow<List<RoomExamEntity>>
    @Query("SELECT * FROM exams WHERE type = :type") suspend fun getByType(type: String): List<RoomExamEntity>
    @Query("SELECT * FROM exams WHERE type = :type") fun observeByType(type: String): Flow<List<RoomExamEntity>>
    @Query("SELECT * FROM exams WHERE teamId = :teamId") suspend fun getByTeamId(teamId: String): List<RoomExamEntity>
    @Query("SELECT * FROM exams WHERE teamId = :teamId AND type = :type") suspend fun getByTeamIdAndType(teamId: String, type: String): List<RoomExamEntity>
    @Query("DELETE FROM exams WHERE id = :id") suspend fun deleteById(id: String): Int
    @Upsert suspend fun upsert(item: RoomExamEntity)
    @Upsert suspend fun upsertAll(items: List<RoomExamEntity>)
    @Upsert fun upsertAllBlocking(items: List<RoomExamEntity>)
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM exam_questions WHERE id IN (:ids)") suspend fun getByIds(ids: List<String>): List<RoomQuestionEntity>
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
    @Query("SELECT * FROM submissions WHERE userId = :userId AND type = 'exam'") suspend fun getExamSubmissionsByUser(userId: String?): List<RoomSubmissionEntity>
    @Query("SELECT * FROM submissions WHERE userId = :userId") fun observeByUserId(userId: String): Flow<List<RoomSubmissionEntity>>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND status = 'pending' AND type = 'survey'") suspend fun getPendingSurveys(userId: String): List<RoomSubmissionEntity>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND LOWER(status) = 'pending' AND type = 'survey'") fun observePendingSurveys(userId: String?): Flow<List<RoomSubmissionEntity>>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND status = 'pending' AND type = 'survey' AND teamId IS NULL") suspend fun getUniquePendingSurveyCandidates(userId: String): List<RoomSubmissionEntity>
    @Query("SELECT COUNT(*) FROM submissions WHERE (isUpdated = 1 OR _id = '')") suspend fun countPendingOfflineSubmissions(): Int
    @Query("SELECT COUNT(*) FROM submissions WHERE LOWER(status) = 'pending' AND id IN (SELECT submissionId FROM answers WHERE submissionId IS NOT NULL)") suspend fun countPendingExamResults(): Int
    @Query("SELECT COUNT(*) FROM submissions WHERE userId = :userId AND parentId = :parentId AND type = :type") suspend fun countByUserParentAndType(userId: String?, parentId: String, type: String): Int
    @Query("SELECT COUNT(*) FROM submissions WHERE userId = :userId AND parentId LIKE '%' || :examId || '%' AND status != 'pending'") suspend fun countCompletedByUserAndExamId(userId: String?, examId: String): Int
    @Query("SELECT * FROM submissions WHERE parentId = :parentId AND userId = :userId AND (:status IS NULL OR status = :status) ORDER BY startTime DESC") suspend fun getByParentUserAndStatus(parentId: String?, userId: String?, status: String?): List<RoomSubmissionEntity>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND parentId = :parentId AND status = 'pending' ORDER BY lastUpdateTime DESC LIMIT 1") suspend fun getLatestPendingByUserAndParent(userId: String?, parentId: String): RoomSubmissionEntity?
    @Query("SELECT * FROM submissions WHERE userId = :userId AND status = 'pending' ORDER BY startTime DESC LIMIT 1") suspend fun getLatestPendingByUser(userId: String?): RoomSubmissionEntity?
    @Query("SELECT * FROM submissions WHERE parentId LIKE '%' || :parentIdFragment || '%' LIMIT 1") suspend fun getFirstByParentIdContaining(parentIdFragment: String): RoomSubmissionEntity?
    @Query("SELECT * FROM submissions WHERE parentId IN (:parentIds) AND type != 'survey' AND uploaded = 0") suspend fun getUnuploadedNonSurveyByParentIds(parentIds: List<String>): List<RoomSubmissionEntity>
    @Query("UPDATE submissions SET user = :userJson, status = 'complete', isUpdated = 1 WHERE id = :id") suspend fun markComplete(id: String, userJson: String): Int
    @Query("UPDATE submissions SET status = :status WHERE id = :id") suspend fun updateStatus(id: String, status: String): Int
    @Query("UPDATE submissions SET status = :status, lastUpdateTime = :lastUpdateTime WHERE id = :id") suspend fun updateStatusAndLastUpdate(id: String, status: String, lastUpdateTime: Long): Int
    @Query("DELETE FROM submissions WHERE parentId = :parentId AND userId = :userId") suspend fun deleteByParentAndUser(parentId: String, userId: String?): Int
    @Query("DELETE FROM submissions WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>): Int
    @Query("DELETE FROM submissions WHERE parentId = :parentId AND userId = :userId AND status = 'pending' AND type = 'survey' AND teamId IS NULL") suspend fun deletePendingSurveyOrphans(parentId: String?, userId: String?): Int
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
    @Query("SELECT * FROM answers WHERE submissionId = :submissionId AND questionId = :questionId LIMIT 1") suspend fun getBySubmissionAndQuestion(submissionId: String, questionId: String?): RoomAnswerEntity?
    @Query("DELETE FROM answers WHERE submissionId IN (:submissionIds)") suspend fun deleteBySubmissionIds(submissionIds: List<String>): Int
    @Upsert suspend fun upsertAll(items: List<RoomAnswerEntity>)
    @Upsert fun upsertAllBlocking(items: List<RoomAnswerEntity>)
}

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams WHERE _id = :teamId OR teamId = :teamId LIMIT 1") suspend fun getByTeamId(teamId: String): RoomTeamEntity?
    @Query("SELECT * FROM teams WHERE _id = :id LIMIT 1") suspend fun getById(id: String): RoomTeamEntity?
    @Query("SELECT * FROM teams WHERE userId = :userId") suspend fun getByUserId(userId: String): List<RoomTeamEntity>
    @Query("SELECT * FROM teams") suspend fun getAll(): List<RoomTeamEntity>
    @Query("SELECT * FROM teams") fun observeAll(): Flow<List<RoomTeamEntity>>
    @Query("SELECT * FROM teams WHERE docType = :docType") suspend fun getByDocType(docType: String): List<RoomTeamEntity>
    @Query("SELECT * FROM teams WHERE docType = :docType") fun observeByDocType(docType: String): Flow<List<RoomTeamEntity>>
    @Query("SELECT * FROM teams WHERE teamId = :teamId AND docType = :docType") suspend fun getByTeamIdAndDocType(teamId: String, docType: String): List<RoomTeamEntity>
    @Query("SELECT * FROM teams WHERE teamId = :teamId AND userId = :userId AND docType = :docType LIMIT 1") suspend fun getByTeamIdUserIdAndDocType(teamId: String, userId: String, docType: String): RoomTeamEntity?
    @Query("SELECT COUNT(*) FROM teams WHERE teamId = :teamId AND userId = :userId AND docType = :docType") suspend fun countByTeamIdUserIdAndDocType(teamId: String, userId: String, docType: String): Int
    @Query("SELECT COUNT(*) FROM teams WHERE teamId = :teamId AND docType = :docType") suspend fun countByTeamIdAndDocType(teamId: String, docType: String): Int
    @Query("DELETE FROM teams WHERE _id = :id") suspend fun deleteById(id: String): Int
    @Query("DELETE FROM teams WHERE teamId = :teamId AND userId = :userId AND docType = :docType") suspend fun deleteByTeamIdUserIdAndDocType(teamId: String, userId: String, docType: String): Int
    @Upsert suspend fun upsertAll(items: List<RoomTeamEntity>)
    @Upsert suspend fun upsert(item: RoomTeamEntity)
}
