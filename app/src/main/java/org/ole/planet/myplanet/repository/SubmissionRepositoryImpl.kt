package org.ole.planet.myplanet.repository

import android.text.TextUtils
import io.realm.Sort
import java.util.Date
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.createSubmission

class SubmissionRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), SubmissionRepository {

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

        val examIds = pendingSurveys.mapNotNull { submission ->
            submission.parentId?.split("@")?.firstOrNull()
        }.distinct()

        if (examIds.isEmpty()) return emptyList()

        val exams = queryList(RealmStepExam::class.java) {
            `in`("id", examIds.toTypedArray())
        }
        val validExamIds = exams.mapNotNull { it.id }.toSet()

        val uniqueSurveys = linkedMapOf<String, RealmSubmission>()
        pendingSurveys.forEach { submission ->
            val examId = submission.parentId?.split("@")?.firstOrNull()
            if (examId != null && validExamIds.contains(examId) && !uniqueSurveys.containsKey(examId)) {
                uniqueSurveys[examId] = submission
            }
        }

        return uniqueSurveys.values.toList()
    }

    override suspend fun getSubmissionCountByUser(userId: String?): Int {
        if (userId == null) return 0

        return count(RealmSubmission::class.java) {
            equalTo("userId", userId)
            equalTo("type", "survey")
            equalTo("status", "pending")
        }.toInt()
    }

    override suspend fun getSurveyTitlesFromSubmissions(
        submissions: List<RealmSubmission>
    ): List<String> {
        val examIds = submissions.mapNotNull { it.parentId?.split("@")?.firstOrNull() }
        if (examIds.isEmpty()) {
            return emptyList()
        }

        val exams = queryList(RealmStepExam::class.java) {
            `in`("id", examIds.toTypedArray())
        }
        val examMap = exams.associate { it.id to (it.name ?: "") }

        return submissions.map { submission ->
            val examId = submission.parentId?.split("@")?.firstOrNull()
            examMap[examId] ?: ""
        }
    }

    override suspend fun getExamMapForSubmissions(
        submissions: List<RealmSubmission>
    ): Map<String?, RealmStepExam> {
        val examIds = submissions.mapNotNull { sub ->
            sub.parentId?.split("@")?.firstOrNull()
        }.distinct()

        if (examIds.isEmpty()) {
            return emptyMap()
        }

        val examMap = queryList(RealmStepExam::class.java) {
            `in`("id", examIds.toTypedArray())
        }.associateBy { it.id }

        return submissions.mapNotNull { sub ->
            val parentId = sub.parentId
            val examId = parentId?.split("@")?.firstOrNull()
            examMap[examId]?.let { parentId to it }
        }.toMap()
    }

    override suspend fun getExamQuestionCount(stepId: String): Int {
        return findByField(RealmStepExam::class.java, "stepId", stepId)?.noOfQuestions ?: 0
    }

    override suspend fun getSubmissionById(id: String): RealmSubmission? {
        return findByField(RealmSubmission::class.java, "id", id)
    }

    override suspend fun getSubmissionsByUserId(userId: String): List<RealmSubmission> {
        return queryList(RealmSubmission::class.java) {
            equalTo("userId", userId)
        }
    }

    override suspend fun createSurveySubmission(examId: String, userId: String?) {
        executeTransaction { realm ->
            val courseId = realm.where(RealmStepExam::class.java).equalTo("id", examId).findFirst()?.courseId
            val parentId = if (!TextUtils.isEmpty(courseId)) {
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

    override suspend fun updateSubmission(id: String, updater: (RealmSubmission) -> Unit) {
        update(RealmSubmission::class.java, "id", id, updater)
    }

}
