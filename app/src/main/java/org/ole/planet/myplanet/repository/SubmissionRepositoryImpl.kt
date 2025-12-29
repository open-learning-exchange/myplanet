package org.ole.planet.myplanet.repository

import android.util.Log
import com.google.gson.JsonParser
import io.realm.Case
import io.realm.Sort
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.createSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.submission.QuestionAnswer
import org.ole.planet.myplanet.ui.submission.SubmissionDetail

class SubmissionRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), SubmissionRepository {

    private fun RealmSubmission.examIdFromParentId(): String? {
        return parentId?.substringBefore("@")
    }

    override suspend fun getPendingSurveysFlow(userId: String?): Flow<List<RealmSubmission>> {
        return queryListFlow(RealmSubmission::class.java) {
            equalTo("userId", userId)
                .equalTo("type", "survey")
                .equalTo("status", "pending", Case.INSENSITIVE)
        }
    }

    override suspend fun getSubmissionsFlow(userId: String): Flow<List<RealmSubmission>> {
        return queryListFlow(RealmSubmission::class.java) {
            equalTo("userId", userId)
        }
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

        return databaseService.withRealmAsync { realm ->
            val pendingSurveys = realm.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .equalTo("status", "pending")
                .equalTo("type", "survey")
                .isNull("membershipDoc")
                .findAll()

            if (pendingSurveys.isEmpty()) {
                return@withRealmAsync emptyList()
            }

            val examIds = pendingSurveys.mapNotNull { it.examIdFromParentId() }.distinct()
            if (examIds.isEmpty()) {
                return@withRealmAsync emptyList()
            }

            val exams = realm.where(RealmStepExam::class.java)
                .`in`("id", examIds.toTypedArray())
                .findAll()
            val validExamIds = exams.mapNotNull { it.id }.toSet()

            val uniqueSurveys = linkedMapOf<String, RealmSubmission>()
            pendingSurveys.forEach { submission ->
                val examId = submission.examIdFromParentId()
                if (examId != null && validExamIds.contains(examId) && !uniqueSurveys.containsKey(examId)) {
                    uniqueSurveys[examId] = submission
                }
            }
            realm.copyFromRealm(uniqueSurveys.values)
        }
    }

    override suspend fun getSurveyTitlesFromSubmissions(
        submissions: List<RealmSubmission>
    ): List<String> {
        val examIds = submissions.mapNotNull { it.examIdFromParentId() }
        if (examIds.isEmpty()) {
            return emptyList()
        }

        return databaseService.withRealmAsync { realm ->
            val exams = realm.where(RealmStepExam::class.java)
                .`in`("id", examIds.toTypedArray())
                .findAll()
            val examMap = exams.associate { it.id to (it.name ?: "") }

            submissions.map { submission ->
                val examId = submission.examIdFromParentId()
                examMap[examId] ?: ""
            }
        }
    }

    override suspend fun getExamMapForSubmissions(
        submissions: List<RealmSubmission>
    ): Map<String?, RealmStepExam> {
        val examIds = submissions.mapNotNull { it.examIdFromParentId() }.distinct()
        if (examIds.isEmpty()) {
            return emptyMap()
        }

        return databaseService.withRealmAsync { realm ->
            val examMap = realm.where(RealmStepExam::class.java)
                .`in`("id", examIds.toTypedArray())
                .findAll()
                .associateBy { it.id }

            val resultMap = submissions.mapNotNull { sub ->
                val parentId = sub.parentId
                val examId = sub.examIdFromParentId()
                examMap[examId]?.let { parentId to realm.copyFromRealm(it) }
            }.toMap()
            resultMap
        }
    }

    override suspend fun getExamQuestionCount(stepId: String): Int {
        return findByField(RealmStepExam::class.java, "stepId", stepId)?.noOfQuestions ?: 0
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
        return queryList(RealmSubmission::class.java, ensureLatest = true) {
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
        try {
            save(submission)
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun markSubmissionComplete(id: String, payload: com.google.gson.JsonObject) {
        Log.d("SubmissionRepository", "markSubmissionComplete called for ID: $id")
        update(RealmSubmission::class.java, "id", id) { sub ->
            sub.user = payload.toString()
            sub.status = "complete"
            sub.isUpdated = true  // Mark for upload
            Log.d("SubmissionRepository", "Submission marked: status=complete, isUpdated=true, _id=${sub._id}")
        }
    }

    override suspend fun getSubmissionDetail(submissionId: String): SubmissionDetail? {
        return databaseService.withRealmAsync { realm ->
            var submission = realm.where(RealmSubmission::class.java)
                .equalTo("id", submissionId)
                .or()
                .equalTo("_id", submissionId)
                .findFirst()

            if (submission == null) {
                submission = realm.where(RealmSubmission::class.java)
                    .contains("parentId", submissionId)
                    .findFirst()
            }

            if (submission == null) {
                return@withRealmAsync null
            }

            val examId = submission.parentId?.substringBefore('@')
            val exam = realm.where(RealmStepExam::class.java)
                .equalTo("id", examId)
                .findFirst()

            val user = realm.where(RealmUserModel::class.java)
                .equalTo("id", submission.userId)
                .findFirst()

            val questions = realm.where(RealmExamQuestion::class.java)
                .equalTo("examId", examId)
                .findAll()

            val questionAnswers = questions.map { question ->
                val answer = submission.answers?.find { it.questionId == question.id }
                val isCorrect = answer != null && question.getCorrectChoice()?.contains(answer.value) == true
                var formattedAnswer: String? = null
                if (answer != null) {
                    if (!answer.value.isNullOrEmpty()) {
                        formattedAnswer = answer.value
                    } else {
                        val choices = answer.valueChoices
                        if (!choices.isNullOrEmpty()) {
                            if (question.type?.startsWith("select") == true && !question.choices.isNullOrEmpty()) {
                                formattedAnswer = choices.map { choiceId ->
                                    try {
                                        val choicesArray = com.google.gson.JsonParser.parseString(question.choices).asJsonArray
                                        val choiceObject = choicesArray.find {
                                            it.isJsonObject && it.asJsonObject.has("id") && it.asJsonObject.get("id").asString == choiceId
                                        }?.asJsonObject
                                        choiceObject?.get("text")?.asString ?: choiceId
                                    } catch (e: Exception) {
                                        choiceId
                                    }
                                }.joinToString(", ")
                            } else {
                                formattedAnswer = choices.joinToString(", ")
                            }
                        }
                    }
                }

                org.ole.planet.myplanet.ui.submission.QuestionAnswer(
                    questionId = question.id,
                    questionHeader = question.header,
                    questionBody = question.body,
                    questionType = question.type,
                    answer = formattedAnswer,
                    answerChoices = answer?.valueChoices?.toList(),
                    isCorrect = isCorrect
                )
            }

            org.ole.planet.myplanet.ui.submission.SubmissionDetail(
                title = exam?.name ?: "Submission Details",
                status = "Status: ${submission.status ?: "Unknown"}",
                date = submission.startTime,
                submittedBy = "Submitted by: ${user?.name ?: "Unknown"}",
                questionAnswers = questionAnswers
            )
        }
    }

    override fun getNormalizedSubmitterName(submission: RealmSubmission): String? {
        return runCatching {
            submission.user?.takeIf { it.isNotBlank() }?.let { userJson ->
                val jsonObject = JsonParser.parseString(userJson).asJsonObject
                if (jsonObject.has("name")) {
                    jsonObject.get("name").asString.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    override suspend fun getAllPendingSubmissions(): List<RealmSubmission> {
        return queryList(RealmSubmission::class.java) {
            equalTo("status", "pending", Case.INSENSITIVE)
        }
    }

    override suspend fun getSubmissionsByParentId(parentId: String?, userId: String?): List<RealmSubmission> {
        return queryList(RealmSubmission::class.java) {
            equalTo("parentId", parentId)
                .equalTo("userId", userId)
                .sort("lastUpdateTime", Sort.DESCENDING)
        }
    }
}