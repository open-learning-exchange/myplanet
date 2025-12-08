package org.ole.planet.myplanet.repository

import io.realm.Case
import io.realm.Realm
import io.realm.RealmList
import io.realm.Sort
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.createSubmission
import org.ole.planet.myplanet.ui.exam.ExamAnswerUtils

class SubmissionRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), SubmissionRepository {

    private fun RealmSubmission.examIdFromParentId(): String? {
        return parentId?.substringBefore("@")
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
        update(RealmSubmission::class.java, "id", id) { sub ->
            sub.user = payload.toString()
            sub.status = "complete"
        }
    }
    override suspend fun saveAnswer(
        submissionId: String?,
        questionId: String?,
        ans: String,
        listAns: Map<String, String>?,
        otherText: String?,
        otherVisible: Boolean,
        type: String,
        index: Int,
        total: Int
    ) {
        executeTransaction { r ->
            val realmSubmission = if (submissionId != null) {
                r.where(RealmSubmission::class.java).equalTo("id", submissionId).findFirst()
            } else {
                r.where(RealmSubmission::class.java)
                    .equalTo("status", "pending")
                    .findAll().lastOrNull()
            }

            val realmQuestion =
                r.where(RealmExamQuestion::class.java).equalTo("id", questionId).findFirst()

            if (realmSubmission != null && realmQuestion != null) {
                val answer = createOrRetrieveAnswer(r, realmSubmission, realmQuestion)
                populateAnswer(answer, realmQuestion, ans, listAns, otherText, otherVisible)

                if (type == "exam") {
                    val isCorrect =
                        ExamAnswerUtils.checkCorrectAnswer(ans, listAns, realmQuestion)
                    answer.isPassed = isCorrect
                    answer.grade = 1
                    if (!isCorrect) {
                        answer.mistakes = answer.mistakes + 1
                    }
                }
                updateSubmissionStatus(r, realmSubmission, index, total, type)
            }
        }
    }


    private fun createOrRetrieveAnswer(
        realm: Realm,
        submission: RealmSubmission?,
        question: RealmExamQuestion,
    ): RealmAnswer {
        val existing = submission?.answers?.find { it.questionId == question.id }

        val ansObj = if (existing != null) {
            existing
        } else {
            val newAnswerId = UUID.randomUUID().toString()
            val newAnswer = realm.createObject(RealmAnswer::class.java, newAnswerId)
            submission?.answers?.add(newAnswer)
            newAnswer
        }

        ansObj.questionId = question.id
        ansObj.submissionId = submission?.id
        ansObj.examId = question.examId
        return ansObj
    }

    private fun updateSubmissionStatus(
        realm: Realm,
        submission: RealmSubmission?,
        index: Int,
        total: Int,
        type: String,
    ) {
        submission?.lastUpdateTime = Date().time
        val isFinal = index == total - 1
        submission?.status = when {
            isFinal && type == "survey" -> "complete"
            isFinal -> "requires grading"
            else -> "pending"
        }

        if (isFinal && type == "survey" && submission != null) {
            realm.where(RealmSubmission::class.java)
                .equalTo("userId", submission.userId)
                .equalTo("parentId", submission.parentId)
                .equalTo("status", "pending")
                .notEqualTo("id", submission.id)
                .findAll()
                .forEach { it.status = "complete" }
        }
    }

    private fun populateAnswer(
        answer: RealmAnswer,
        question: RealmExamQuestion,
        ans: String,
        listAns: Map<String, String>?,
        otherText: String?,
        otherVisible: Boolean,
    ) {
        when {
            question.type.equals("select", ignoreCase = true) -> {
                populateSelectAnswer(answer, question, ans, otherText, otherVisible)
            }

            question.type.equals("selectMultiple", ignoreCase = true) -> {
                populateMultipleSelectAnswer(answer, listAns, otherText, otherVisible)
            }

            else -> {
                val textValue = if (otherVisible && !otherText.isNullOrEmpty()) {
                    otherText
                } else {
                    ans
                }
                populateTextAnswer(answer, textValue)
            }
        }
    }

    private fun populateSelectAnswer(
        answer: RealmAnswer,
        question: RealmExamQuestion,
        ans: String,
        otherText: String?,
        otherVisible: Boolean,
    ) {
        if (otherVisible && !otherText.isNullOrEmpty()) {
            answer.value = otherText
            answer.valueChoices = RealmList<String>().apply {
                add("""{"id":"other","text":"$otherText"}""")
            }
        } else {
            val choiceText = ExamAnswerUtils.getChoiceTextById(question, ans)
            answer.value = choiceText
            answer.valueChoices = RealmList<String>().apply {
                if (ans.isNotEmpty()) {
                    add("""{"id":"$ans","text":"$choiceText"}""")
                }
            }
        }
    }

    private fun populateMultipleSelectAnswer(
        answer: RealmAnswer,
        listAns: Map<String, String>?,
        otherText: String?,
        otherVisible: Boolean
    ) {
        answer.value = ""
        answer.valueChoices = RealmList<String>().apply {
            listAns?.forEach { (text, id) ->
                if (id == "other" && otherVisible && !otherText.isNullOrEmpty()) {
                    add("""{"id":"other","text":"$otherText"}""")
                } else {
                    add("""{"id":"$id","text":"$text"}""")
                }
            }
        }
    }

    private fun populateTextAnswer(answer: RealmAnswer, ans: String) {
        answer.value = ans
        answer.valueChoices = null
    }
}
