package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.Submission

@Dao
interface SubmissionDao {
    @Query("SELECT * FROM submissions WHERE id = :id OR _id = :id LIMIT 1") suspend fun getByIdOrRemoteId(id: String): Submission?
    @Query("SELECT * FROM submissions WHERE id IN (:ids)") suspend fun getByIds(ids: List<String>): List<Submission>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND teamId = :teamId") suspend fun getByUserIdAndTeamId(userId: String, teamId: String): List<Submission>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND teamId IS NULL") suspend fun getByUserIdWithoutTeam(userId: String): List<Submission>
    @Query("SELECT * FROM submissions WHERE userId IS :userId AND type = 'exam'") suspend fun getExamSubmissionsByUser(userId: String?): List<Submission>
    @Query("SELECT * FROM submissions WHERE userId = :userId") fun observeByUserId(userId: String): Flow<List<Submission>>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND status = 'pending' AND type = 'survey'") suspend fun getPendingSurveys(userId: String): List<Submission>
    @Query("SELECT COUNT(*) FROM submissions WHERE userId = :userId AND status = 'pending' AND type = 'survey'") suspend fun countPendingSurveys(userId: String): Int
    @Query("SELECT * FROM submissions WHERE userId = :userId AND LOWER(status) = 'pending' AND type = 'survey'") fun observePendingSurveys(userId: String?): Flow<List<Submission>>
    @Query("SELECT * FROM submissions WHERE userId = :userId AND status = 'pending' AND type = 'survey' AND teamId IS NULL") suspend fun getUniquePendingSurveyCandidates(userId: String): List<Submission>
    @Query("SELECT COUNT(*) FROM submissions WHERE (isUpdated = 1 OR _id IS NULL OR _id = '')") suspend fun countPendingOfflineSubmissions(): Int
    @Query("SELECT COUNT(*) FROM submissions WHERE LOWER(status) = 'pending' AND id IN (SELECT submissionId FROM answers WHERE submissionId IS NOT NULL)") suspend fun countPendingExamResults(): Int
    @Query("SELECT COUNT(*) FROM submissions WHERE userId IS :userId AND parentId = :parentId AND type = :type") suspend fun countByUserParentAndType(userId: String?, parentId: String, type: String): Int
    @Query("SELECT COUNT(*) FROM submissions WHERE userId IS :userId AND parentId LIKE '%' || :examId || '%' AND status != 'pending'") suspend fun countCompletedByUserAndExamId(userId: String?, examId: String): Int
    @Query("SELECT * FROM submissions WHERE parentId IS :parentId AND userId IS :userId AND (:status IS NULL OR status = :status) ORDER BY startTime DESC") suspend fun getByParentUserAndStatus(parentId: String?, userId: String?, status: String?): List<Submission>
    @Query("SELECT * FROM submissions WHERE teamId = :teamId") suspend fun getByTeamId(teamId: String): List<Submission>
    @Query("SELECT * FROM submissions WHERE parentId IN (:parentIds) AND teamId = :teamId") suspend fun getByParentIdsAndTeamId(parentIds: List<String>, teamId: String): List<Submission>
    @Query("SELECT * FROM submissions WHERE userId IS :userId AND parentId = :parentId AND status = 'pending' ORDER BY lastUpdateTime DESC LIMIT 1") suspend fun getLatestPendingByUserAndParent(userId: String?, parentId: String): Submission?
    @Query("SELECT * FROM submissions WHERE parentId = :parentId AND status = :status ORDER BY lastUpdateTime DESC LIMIT 1") suspend fun getLatestByParentIdAndStatus(parentId: String, status: String): Submission?
    @Query("SELECT * FROM submissions WHERE userId IS :userId AND status = 'pending' ORDER BY startTime DESC LIMIT 1") suspend fun getLatestPendingByUser(userId: String?): Submission?
    @Query("SELECT * FROM submissions WHERE parentId LIKE '%' || :parentIdFragment || '%' LIMIT 1") suspend fun getFirstByParentIdContaining(parentIdFragment: String): Submission?
    @Query("SELECT * FROM submissions WHERE parentId IN (:parentIds) AND type != 'survey' AND uploaded = 0") suspend fun getUnuploadedNonSurveyByParentIds(parentIds: List<String>): List<Submission>
    @Query("SELECT * FROM submissions WHERE userId IN (:userIds) AND parentId = :parentId AND status = 'pending' ORDER BY lastUpdateTime DESC") suspend fun getPendingByUsersAndParent(userIds: List<String>, parentId: String): List<Submission>
    @Query("UPDATE submissions SET user = :userJson, status = 'complete', isUpdated = 1 WHERE id = :id") suspend fun markComplete(id: String, userJson: String): Int
    @Query("UPDATE submissions SET status = :status, isUpdated = 1 WHERE id = :id") suspend fun updateStatus(id: String, status: String): Int
    @Query("UPDATE submissions SET status = :status, lastUpdateTime = :lastUpdateTime, isUpdated = 1 WHERE id = :id") suspend fun updateStatusAndLastUpdate(id: String, status: String, lastUpdateTime: Long): Int
    @Query("DELETE FROM submissions WHERE parentId = :parentId AND userId IS :userId") suspend fun deleteByParentAndUser(parentId: String, userId: String?): Int
    @Query("DELETE FROM submissions WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>): Int
    @Query("DELETE FROM submissions WHERE parentId IS :parentId AND userId IS :userId AND status = 'pending' AND type = 'survey' AND teamId IS NULL") suspend fun deletePendingSurveyOrphans(parentId: String?, userId: String?): Int
    @Query("SELECT * FROM submissions WHERE type = 'exam' AND parentId IS NOT NULL AND userId IS NOT NULL AND (_id IS NULL OR _id = '')") suspend fun getPendingExamResults(): List<Submission>
    @Query("SELECT * FROM submissions WHERE status = 'complete' AND (isUpdated = 1 OR _id IS NULL OR _id = '')") suspend fun getPendingSubmissions(): List<Submission>
    @Upsert suspend fun upsertAll(items: List<Submission>)
    @Upsert fun upsertAllBlocking(items: List<Submission>)
    @Query("UPDATE submissions SET _id = :remoteId, _rev = :remoteRev, isUpdated = 0 WHERE id = :localId") suspend fun markUploaded(localId: String, remoteId: String?, remoteRev: String?): Int
}