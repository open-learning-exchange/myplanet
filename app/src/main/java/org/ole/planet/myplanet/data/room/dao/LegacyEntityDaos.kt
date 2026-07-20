package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.Answer
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.UserEntity

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id OR _id = :id LIMIT 1")
    suspend fun getById(id: String): UserEntity?
    @Query("SELECT * FROM users") suspend fun getAll(): List<UserEntity>
    @Query("SELECT * FROM users WHERE name = :name LIMIT 1") suspend fun getByName(name: String): UserEntity?
    @Query("SELECT * FROM users WHERE name LIKE '%' || :query || '%' OR firstName LIKE '%' || :query || '%' OR lastName LIKE '%' || :query || '%'") suspend fun search(query: String): List<UserEntity>
    @Query("SELECT COUNT(*) FROM users") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM users WHERE planetCode = :planetCode") suspend fun countByPlanetCode(planetCode: String): Int
    @Query("DELETE FROM users WHERE id = :id") suspend fun deleteById(id: String): Int
    @Upsert suspend fun upsert(item: UserEntity)
    @Upsert suspend fun upsertAll(items: List<UserEntity>)
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses") suspend fun getAll(): List<MyCourse>
    @Query("SELECT * FROM courses WHERE courseId = :courseId OR id = :courseId LIMIT 1") suspend fun getByCourseId(courseId: String): MyCourse?
    @Query("SELECT * FROM courses WHERE courseId IN (:courseIds)") suspend fun getByCourseIds(courseIds: List<String>): List<MyCourse>
    @Query("SELECT * FROM courses") fun observeAll(): Flow<List<MyCourse>>
    @Query("SELECT * FROM courses WHERE courseId = :courseId OR id = :courseId LIMIT 1") fun observeByCourseId(courseId: String): Flow<MyCourse?>
    @Query("DELETE FROM courses WHERE courseId = :courseId") suspend fun deleteByCourseId(courseId: String): Int
    @Upsert suspend fun upsertAll(items: List<MyCourse>)
    @Upsert fun upsertAllBlocking(items: List<MyCourse>)
    @Upsert suspend fun upsert(item: MyCourse)
}

@Dao
interface CourseStepDao {
    @Query("SELECT * FROM course_steps WHERE courseId = :courseId") suspend fun getByCourseId(courseId: String): List<CourseStep>
    @Query("SELECT * FROM course_steps WHERE courseId IN (:courseIds)") suspend fun getByCourseIds(courseIds: List<String>): List<CourseStep>
    @Query("SELECT * FROM course_steps WHERE id = :id LIMIT 1") suspend fun getById(id: String): CourseStep?
    @Upsert suspend fun upsertAll(items: List<CourseStep>)
    @Upsert fun upsertAllBlocking(items: List<CourseStep>)
}

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

@Dao
interface QuestionDao {
    @Query("SELECT * FROM exam_questions WHERE id IN (:ids)") suspend fun getByIds(ids: List<String>): List<ExamQuestion>
    @Query("SELECT * FROM exam_questions WHERE examId = :examId") suspend fun getByExamId(examId: String): List<ExamQuestion>
    @Query("SELECT * FROM exam_questions WHERE examId IN (:examIds)") suspend fun getByExamIds(examIds: List<String>): List<ExamQuestion>
    @Query("SELECT COUNT(*) FROM exam_questions WHERE examId = :examId") suspend fun countByExamId(examId: String): Int
    @Upsert suspend fun upsertAll(items: List<ExamQuestion>)
    @Upsert fun upsertAllBlocking(items: List<ExamQuestion>)
}

@Dao
interface SubmissionDao {
    @Query("SELECT * FROM submissions WHERE id = :id OR _id = :id LIMIT 1") suspend fun getByIdOrRemoteId(id: String): Submission?
    @Query("SELECT * FROM submissions WHERE id IN (:ids)") suspend fun getByIds(ids: List<String>): List<Submission>
    @Query("SELECT * FROM submissions WHERE userId = :userId") suspend fun getByUserId(userId: String): List<Submission>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND teamId = :teamId") suspend fun getByUserIdAndTeamId(userId: String, teamId: String): List<Submission>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND teamId IS NULL") suspend fun getByUserIdWithoutTeam(userId: String): List<Submission>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND type = 'exam'") suspend fun getExamSubmissionsByUser(userId: String?): List<Submission>
    @Query("SELECT * FROM submissions WHERE userId = :userId") fun observeByUserId(userId: String): Flow<List<Submission>>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND status = 'pending' AND type = 'survey'") suspend fun getPendingSurveys(userId: String): List<Submission>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND LOWER(status) = 'pending' AND type = 'survey'") fun observePendingSurveys(userId: String?): Flow<List<Submission>>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND status = 'pending' AND type = 'survey' AND teamId IS NULL") suspend fun getUniquePendingSurveyCandidates(userId: String): List<Submission>
    @Query("SELECT COUNT(*) FROM submissions WHERE (isUpdated = 1 OR _id = '')") suspend fun countPendingOfflineSubmissions(): Int
    @Query("SELECT COUNT(*) FROM submissions WHERE LOWER(status) = 'pending' AND id IN (SELECT submissionId FROM answers WHERE submissionId IS NOT NULL)") suspend fun countPendingExamResults(): Int
    @Query("SELECT COUNT(*) FROM submissions WHERE userId = :userId AND parentId = :parentId AND type = :type") suspend fun countByUserParentAndType(userId: String?, parentId: String, type: String): Int
    @Query("SELECT COUNT(*) FROM submissions WHERE userId = :userId AND parentId LIKE '%' || :examId || '%' AND status != 'pending'") suspend fun countCompletedByUserAndExamId(userId: String?, examId: String): Int
    @Query("SELECT * FROM submissions WHERE parentId = :parentId AND userId = :userId AND (:status IS NULL OR status = :status) ORDER BY startTime DESC") suspend fun getByParentUserAndStatus(parentId: String?, userId: String?, status: String?): List<Submission>
    @Query("SELECT * FROM submissions WHERE teamId = :teamId") suspend fun getByTeamId(teamId: String): List<Submission>
    @Query("SELECT * FROM submissions WHERE parentId IN (:parentIds) AND teamId = :teamId") suspend fun getByParentIdsAndTeamId(parentIds: List<String>, teamId: String): List<Submission>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND parentId = :parentId AND status = 'pending' ORDER BY lastUpdateTime DESC LIMIT 1") suspend fun getLatestPendingByUserAndParent(userId: String?, parentId: String): Submission?
    @Query("SELECT * FROM submissions WHERE userId = :userId AND status = 'pending' ORDER BY startTime DESC LIMIT 1") suspend fun getLatestPendingByUser(userId: String?): Submission?
    @Query("SELECT * FROM submissions WHERE parentId LIKE '%' || :parentIdFragment || '%' LIMIT 1") suspend fun getFirstByParentIdContaining(parentIdFragment: String): Submission?
    @Query("SELECT * FROM submissions WHERE parentId IN (:parentIds) AND type != 'survey' AND uploaded = 0") suspend fun getUnuploadedNonSurveyByParentIds(parentIds: List<String>): List<Submission>
    @Query("UPDATE submissions SET user = :userJson, status = 'complete', isUpdated = 1 WHERE id = :id") suspend fun markComplete(id: String, userJson: String): Int
    @Query("UPDATE submissions SET status = :status WHERE id = :id") suspend fun updateStatus(id: String, status: String): Int
    @Query("UPDATE submissions SET status = :status, lastUpdateTime = :lastUpdateTime WHERE id = :id") suspend fun updateStatusAndLastUpdate(id: String, status: String, lastUpdateTime: Long): Int
    @Query("DELETE FROM submissions WHERE parentId = :parentId AND userId = :userId") suspend fun deleteByParentAndUser(parentId: String, userId: String?): Int
    @Query("DELETE FROM submissions WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>): Int
    @Query("DELETE FROM submissions WHERE parentId = :parentId AND userId = :userId AND status = 'pending' AND type = 'survey' AND teamId IS NULL") suspend fun deletePendingSurveyOrphans(parentId: String?, userId: String?): Int
    @Query("SELECT * FROM submissions WHERE type = 'exam' AND parentId IS NOT NULL AND userId IS NOT NULL AND (_id IS NULL OR _id = '')") suspend fun getPendingExamResults(): List<Submission>
    @Query("SELECT * FROM submissions WHERE status = 'complete' AND (isUpdated = 1 OR _id = '')") suspend fun getPendingSubmissions(): List<Submission>
    @Upsert suspend fun upsertAll(items: List<Submission>)
    @Upsert fun upsertAllBlocking(items: List<Submission>)
    @Query("UPDATE submissions SET _id = :remoteId, _rev = :remoteRev, isUpdated = 0 WHERE id = :localId") suspend fun markUploaded(localId: String, remoteId: String?, remoteRev: String?): Int
}

@Dao
interface AnswerDao {
    @Query("SELECT * FROM answers WHERE submissionId = :submissionId") suspend fun getBySubmissionId(submissionId: String): List<Answer>
    @Query("SELECT * FROM answers WHERE submissionId IN (:submissionIds)") suspend fun getBySubmissionIds(submissionIds: List<String>): List<Answer>
    @Query("SELECT * FROM answers WHERE submissionId = :submissionId AND questionId = :questionId LIMIT 1") suspend fun getBySubmissionAndQuestion(submissionId: String, questionId: String?): Answer?
    @Query("DELETE FROM answers WHERE submissionId IN (:submissionIds)") suspend fun deleteBySubmissionIds(submissionIds: List<String>): Int
    @Upsert suspend fun upsertAll(items: List<Answer>)
    @Upsert fun upsertAllBlocking(items: List<Answer>)
}

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams WHERE _id = :teamId OR teamId = :teamId LIMIT 1") suspend fun getByTeamId(teamId: String): MyTeam?
    @Query("SELECT * FROM teams WHERE _id = :id LIMIT 1") suspend fun getById(id: String): MyTeam?
    @Query("SELECT * FROM teams WHERE userId = :userId") suspend fun getByUserId(userId: String): List<MyTeam>
    @Query("SELECT * FROM teams") suspend fun getAll(): List<MyTeam>
    @Query("SELECT * FROM teams") fun observeAll(): Flow<List<MyTeam>>
    @Query("SELECT * FROM teams WHERE docType = :docType") suspend fun getByDocType(docType: String): List<MyTeam>
    @Query("SELECT * FROM teams WHERE docType = :docType") fun observeByDocType(docType: String): Flow<List<MyTeam>>
    @Query("SELECT * FROM teams WHERE teamId = :teamId AND docType = :docType") suspend fun getByTeamIdAndDocType(teamId: String, docType: String): List<MyTeam>
    @Query("SELECT * FROM teams WHERE teamId = :teamId AND userId = :userId AND docType = :docType LIMIT 1") suspend fun getByTeamIdUserIdAndDocType(teamId: String, userId: String, docType: String): MyTeam?
    @Query("SELECT COUNT(*) FROM teams WHERE teamId = :teamId AND userId = :userId AND docType = :docType") suspend fun countByTeamIdUserIdAndDocType(teamId: String, userId: String, docType: String): Int
    @Query("SELECT COUNT(*) FROM teams WHERE teamId = :teamId AND docType = :docType") suspend fun countByTeamIdAndDocType(teamId: String, docType: String): Int
    @Query("DELETE FROM teams WHERE _id = :id") suspend fun deleteById(id: String): Int
    @Query("DELETE FROM teams WHERE teamId = :teamId AND userId = :userId AND docType = :docType") suspend fun deleteByTeamIdUserIdAndDocType(teamId: String, userId: String, docType: String): Int
    @Upsert suspend fun upsertAll(items: List<MyTeam>)
    @Upsert suspend fun upsert(item: MyTeam)
}
