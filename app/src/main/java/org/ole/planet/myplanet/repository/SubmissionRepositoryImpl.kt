package org.ole.planet.myplanet.repository

import io.realm.Case
import io.realm.Sort
import java.util.Date
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.createSubmission

class SubmissionRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), SubmissionRepository {

    private fun RealmSubmission.examIdFromParentId(): String? {
        return parentId?.substringBefore("@")
    }

    override suspend fun getPendingSurveys(userId: String?): List<RealmSubmission> {
        if (userId == null) return emptyList()

        return queryList(RealmSubmission::class.java) {
            equalTo("userId", userId)
            equalTo("status", "pending")
            equalTo("type", "survey")
        }
    }

    override suspend fun getUniquePendingSurveys(userId: String?): List<RealmSubmission> {
        if (userId == null) return emptyList()

        val pendingSurveys = getPendingSurveys(userId)
        if (pendingSurveys.isEmpty()) return emptyList()

        val examIds = pendingSurveys.mapNotNull { it.examIdFromParentId() }.distinct()

        if (examIds.isEmpty()) return emptyList()

        val exams = queryList(RealmStepExam::class.java) {
            `in`("id", examIds.toTypedArray())
        }
        val validExamIds = exams.mapNotNull { it.id }.toSet()

        val uniqueSurveys = linkedMapOf<String, RealmSubmission>()
        pendingSurveys.forEach { submission ->
            val examId = submission.examIdFromParentId()
            if (examId != null && validExamIds.contains(examId) && !uniqueSurveys.containsKey(examId)) {
                uniqueSurveys[examId] = submission
            }
        }

        return uniqueSurveys.values.toList()
    }

    override suspend fun getSurveyTitlesFromSubmissions(
        submissions: List<RealmSubmission>
    ): List<String> {
        val examIds = submissions.mapNotNull { it.examIdFromParentId() }
        if (examIds.isEmpty()) {
            return emptyList()
        }

        val exams = queryList(RealmStepExam::class.java) {
            `in`("id", examIds.toTypedArray())
        }
        val examMap = exams.associate { it.id to (it.name ?: "") }

        return submissions.map { submission ->
            val examId = submission.examIdFromParentId()
            examMap[examId] ?: ""
        }
    }

    override suspend fun getExamMapForSubmissions(
        submissions: List<RealmSubmission>
    ): Map<String?, RealmStepExam> {
        val examIds = submissions.mapNotNull { it.examIdFromParentId() }.distinct()

        if (examIds.isEmpty()) {
            return emptyMap()
        }

        val examMap = queryList(RealmStepExam::class.java) {
            `in`("id", examIds.toTypedArray())
        }.associateBy { it.id }

        return submissions.mapNotNull { sub ->
            val parentId = sub.parentId
            val examId = sub.examIdFromParentId()
            examMap[examId]?.let { parentId to it }
        }.toMap()
    }

    override suspend fun getExamQuestionCount(stepId: String): Int {
        return findByField(RealmStepExam::class.java, "stepId", stepId)?.noOfQuestions ?: 0
    }

    override suspend fun findStepExamByName(name: String): RealmStepExam? {
        return findByField(RealmStepExam::class.java, "name", name)
    }

    override suspend fun getSubmissionById(id: String): RealmSubmission? {
        return findByField(RealmSubmission::class.java, "id", id)
    }

    override suspend fun getSubmissionsByIds(ids: List<String>): List<RealmSubmission> {
        if (ids.isEmpty()) return emptyList()

        return queryList(RealmSubmission::class.java) {
            `in`("id", ids.toTypedArray())
        }
    }

    override suspend fun getSubmissionsByUserId(userId: String): List<RealmSubmission> {
        return queryList(RealmSubmission::class.java) {
            equalTo("userId", userId)
        }
    }

    override suspend fun hasSubmission(
        stepExamId: String?,
        courseId: String?,
        userId: String?,
        type: String,
    ): Boolean {
        if (stepExamId.isNullOrBlank() || courseId.isNullOrBlank() || userId.isNullOrBlank()) {
            return false
        }

        val questions = queryList(RealmExamQuestion::class.java) {
            equalTo("examId", stepExamId)
        }
        if (questions.isEmpty()) {
            return false
        }

        val examId = questions.firstOrNull()?.examId
        if (examId.isNullOrBlank()) {
            return false
        }

        val parentId = "$examId@$courseId"
        return count(RealmSubmission::class.java) {
            equalTo("userId", userId)
            equalTo("parentId", parentId)
            equalTo("type", type)
        } > 0
    }

    override suspend fun hasPendingOfflineSubmissions(): Boolean {
        return count(RealmSubmission::class.java) {
            beginGroup()
            equalTo("isUpdated", true)
            or()
            isEmpty("_id")
            endGroup()
        } > 0
    }

    override suspend fun hasPendingExamResults(): Boolean {
        return count(RealmSubmission::class.java) {
            equalTo("status", "pending", Case.INSENSITIVE)
            isNotEmpty("answers")
        } > 0
    }

    override suspend fun createSurveySubmission(examId: String, userId: String?) {
        executeTransaction { realm ->
            val courseId = realm.where(RealmStepExam::class.java).equalTo("id", examId).findFirst()?.courseId
            val parentId = if (!courseId.isNullOrEmpty()) {
                examId + "@" + courseId
            } else {
                examId
            }
            var sub = realm.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .equalTo(
                    "parentId",
                    parentId,
                )
                .sort("lastUpdateTime", Sort.DESCENDING)
                .equalTo("status", "pending")
                .findFirst()
            sub = createSubmission(sub, realm)
            sub.parentId = parentId
            sub.userId = userId
            sub.type = "survey"
            sub.status = "pending"
            sub.startTime = Date().time
        }
    }

    override suspend fun saveSubmission(submission: RealmSubmission) {
        save(submission)
    }

}
