package org.ole.planet.myplanet.repository

import android.util.Log
import com.google.gson.JsonParser
import io.realm.Case
import io.realm.RealmList
import io.realm.Sort
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.QuestionAnswer
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMembershipDoc
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.createSubmission
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmTeamReference
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.SubmissionDetail
import org.ole.planet.myplanet.model.SubmissionItem
import org.ole.planet.myplanet.utils.ExamAnswerUtils
import org.ole.planet.myplanet.utils.NetworkUtils

class SubmissionsRepositoryImpl @Inject internal constructor(
    databaseService: DatabaseService,
    private val submissionsRepositoryExporter: SubmissionsRepositoryExporter,
    private val teamsRepositoryProvider: Provider<TeamsRepository>
) : RealmRepository(databaseService), SubmissionsRepository {

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

    override suspend fun getExamMap(
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
        Log.d("SubmissionsRepository", "markSubmissionComplete called for ID: $id")
        update(RealmSubmission::class.java, "id", id) { sub ->
            sub.user = payload.toString()
            sub.status = "complete"
            sub.isUpdated = true // Mark for upload
            Log.d("SubmissionsRepository", "Submission marked: status=complete, isUpdated=true, _id=${sub._id}")
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

            val user = realm.where(RealmUser::class.java)
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

                QuestionAnswer(
                    questionId = question.id,
                    questionHeader = question.header,
                    questionBody = question.body,
                    questionType = question.type,
                    answer = formattedAnswer,
                    answerChoices = answer?.valueChoices?.toList(),
                    isCorrect = isCorrect
                )
            }

            SubmissionDetail(
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

    override suspend fun getSubmissionsByParentId(parentId: String?, userId: String?, status: String?): List<RealmSubmission> {
        return queryList(RealmSubmission::class.java) {
            equalTo("parentId", parentId)
                .equalTo("userId", userId)
                .apply {
                    if (status != null) {
                        equalTo("status", status)
                    }
                }
                .sort("startTime", Sort.DESCENDING)
        }
    }

    override suspend fun getSubmissionItems(parentId: String?, userId: String?): List<SubmissionItem> {
        return getSubmissionsByParentId(parentId, userId).map {
            SubmissionItem(
                id = it.id,
                lastUpdateTime = it.lastUpdateTime,
                status = it.status ?: "",
                uploaded = it.uploaded
            )
        }
    }

    override suspend fun deleteExamSubmissions(examId: String, courseId: String?, userId: String?) {
        databaseService.executeTransactionAsync { realm ->
            val parentIdToSearch = if (!courseId.isNullOrEmpty()) {
                "${examId}@${courseId}"
            } else {
                examId
            }

            val allSubmissions = realm.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .equalTo("parentId", parentIdToSearch)
                .findAll()

            allSubmissions.forEach { submission ->
                submission.answers?.deleteAllFromRealm()
                submission.deleteFromRealm()
            }
        }
    }

    override suspend fun isStepCompleted(stepId: String?, userId: String?): Boolean {
        if (stepId == null) return true
        val exam = findByField<RealmStepExam, String>(RealmStepExam::class.java, "stepId", stepId) ?: return true
        return exam.id?.let {
            count(RealmSubmission::class.java) {
                equalTo("userId", userId)
                    .contains("parentId", it)
                    .notEqualTo("status", "pending")
            } > 0
        } ?: false
    }

    override suspend fun getSurveysByCourseId(courseId: String): List<RealmStepExam> {
        return queryList(RealmStepExam::class.java) {
            equalTo("courseId", courseId)
            equalTo("type", "survey")
        }
    }

    override suspend fun hasUnfinishedSurveys(courseId: String, userId: String?): Boolean {
        val surveys = getSurveysByCourseId(courseId)
        for (survey in surveys) {
            if (!hasSubmission(survey.id, courseId, userId, "survey")) {
                return true
            }
        }
        return false
    }

    override suspend fun generateSubmissionPdf(context: android.content.Context, submissionId: String): java.io.File? {
        return submissionsRepositoryExporter.generateSubmissionPdf(context, submissionId)
    }

    override suspend fun generateMultipleSubmissionsPdf(
        context: android.content.Context,
        submissionIds: List<String>,
        examTitle: String
    ): java.io.File? {
        return submissionsRepositoryExporter.generateMultipleSubmissionsPdf(context, submissionIds, examTitle)
    }

    override suspend fun addSubmissionPhoto(
        submissionId: String?,
        examId: String?,
        courseId: String?,
        memberId: String?,
        photoPath: String?
    ) {
        executeTransaction { realm ->
            val id = UUID.randomUUID().toString()
            val submit = realm.createObject(RealmSubmitPhotos::class.java, id)
            submit.submissionId = submissionId
            submit.examId = examId
            submit.courseId = courseId
            submit.memberId = memberId
            submit.date = Date().toString()
            submit.uniqueId = NetworkUtils.getUniqueIdentifier()
            submit.photoLocation = photoPath
            submit.uploaded = false
        }
    }

    override suspend fun createExamSubmission(userId: String?, userDob: String?, userGender: String?, exam: RealmStepExam, type: String?, teamId: String?): RealmSubmission? {
        val team = if (!teamId.isNullOrEmpty()) {
            teamsRepositoryProvider.get().getTeamById(teamId)
        } else {
            null
        }

        return databaseService.withRealmAsync { realm ->
            var detachedSub: RealmSubmission? = null
            realm.executeTransaction { r ->
                val managedSub = createSubmission(null, r)

                val parentId = when {
                    !exam.id.isNullOrEmpty() -> if (!exam.courseId.isNullOrEmpty()) {
                        "${exam.id}@${exam.courseId}"
                    } else {
                        exam.id
                    }
                    else -> managedSub.parentId
                }
                managedSub.parentId = parentId

                try {
                    val parentJsonString = com.google.gson.JsonObject().apply {
                        addProperty("_id", exam.id ?: "")
                        addProperty("name", exam.name ?: "")
                        addProperty("courseId", exam.courseId ?: "")
                        addProperty("sourcePlanet", exam.sourcePlanet ?: "")
                        addProperty("teamShareAllowed", exam.isTeamShareAllowed)
                        addProperty("noOfQuestions", exam.noOfQuestions)
                        addProperty("isFromNation", exam.isFromNation)
                    }.toString()
                    managedSub.parent = parentJsonString
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                managedSub.userId = userId
                managedSub.status = "pending"
                managedSub.type = type
                managedSub.startTime = Date().time
                managedSub.lastUpdateTime = Date().time
                if (managedSub.answers == null) {
                    managedSub.answers = RealmList()
                }

                if (team != null) {
                    val teamRef = r.createObject(RealmTeamReference::class.java)
                    teamRef._id = team._id
                    teamRef.name = team.name
                    teamRef.type = team.type ?: "team"
                    managedSub.teamObject = teamRef

                    val membershipDoc = r.createObject(RealmMembershipDoc::class.java)
                    membershipDoc.teamId = teamId
                    managedSub.membershipDoc = membershipDoc

                    try {
                        val userJson = com.google.gson.JsonObject()
                        userJson.addProperty("age", userDob ?: "")
                        userJson.addProperty("gender", userGender ?: "")
                        val membershipJson = com.google.gson.JsonObject()
                        membershipJson.addProperty("teamId", teamId)
                        userJson.add("membershipDoc", membershipJson)
                        managedSub.user = userJson.toString()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                detachedSub = r.copyFromRealm(managedSub)
            }
            detachedSub
        }
    }

    override suspend fun saveExamAnswer(
        submission: RealmSubmission?,
        question: RealmExamQuestion,
        ans: String,
        listAns: Map<String, String>?,
        otherText: String?,
        otherVisible: Boolean,
        type: String,
        index: Int,
        total: Int,
        isExplicitSubmission: Boolean
    ): Boolean {
        val submissionId = submission?.id
        val questionId = question.id

        databaseService.executeTransactionAsync { r ->
            val realmSubmission = if (submissionId != null) {
                r.where(RealmSubmission::class.java).equalTo("id", submissionId).findFirst()
            } else {
                r.where(RealmSubmission::class.java)
                    .equalTo("status", "pending")
                    .findAll().lastOrNull()
            }

            val realmQuestion = r.where(RealmExamQuestion::class.java).equalTo("id", questionId).findFirst()

            if (realmSubmission != null && realmQuestion != null) {
                val existing = realmSubmission.answers?.find { it.questionId == realmQuestion.id }
                val ansObj = if (existing != null) {
                    existing
                } else {
                    val newAnswerId = UUID.randomUUID().toString()
                    val newAnswer = r.createObject(RealmAnswer::class.java, newAnswerId)
                    realmSubmission.answers?.add(newAnswer)
                    newAnswer
                }
                ansObj.questionId = realmQuestion.id
                ansObj.submissionId = realmSubmission.id
                ansObj.examId = realmQuestion.examId

                if (realmQuestion.type.equals("select", ignoreCase = true)) {
                    if (otherVisible && !otherText.isNullOrEmpty()) {
                        ansObj.value = otherText
                        ansObj.valueChoices = RealmList<String>().apply {
                            add("""{"id":"other","text":"$otherText"}""")
                        }
                    } else {
                        val choiceText = ExamAnswerUtils.getChoiceTextById(realmQuestion, ans)
                        ansObj.value = choiceText
                        ansObj.valueChoices = RealmList<String>().apply {
                            if (ans.isNotEmpty()) {
                                add("""{"id":"$ans","text":"$choiceText"}""")
                            }
                        }
                    }
                } else if (realmQuestion.type.equals("selectMultiple", ignoreCase = true)) {
                    ansObj.value = ""
                    ansObj.valueChoices = RealmList<String>().apply {
                        listAns?.forEach { (text, id) ->
                            if (id == "other" && otherVisible && !otherText.isNullOrEmpty()) {
                                add("""{"id":"other","text":"$otherText"}""")
                            } else {
                                add("""{"id":"$id","text":"$text"}""")
                            }
                        }
                    }
                } else {
                    val textValue = if (otherVisible && !otherText.isNullOrEmpty()) {
                        otherText
                    } else {
                        ans
                    }
                    ansObj.value = textValue
                    ansObj.valueChoices = null
                }

                if (type == "exam") {
                    val isCorrect = ExamAnswerUtils.checkCorrectAnswer(ans, listAns, realmQuestion)
                    ansObj.isPassed = isCorrect
                    ansObj.grade = 1
                    if (!isCorrect) {
                        ansObj.mistakes += 1
                    }
                }

                val isFinal = index == total - 1
                realmSubmission.lastUpdateTime = Date().time
                realmSubmission.status = when {
                    isFinal && isExplicitSubmission && type == "survey" -> "complete"
                    isFinal && isExplicitSubmission -> "requires grading"
                    else -> "pending"
                }
            }
        }

        if (type == "exam") {
            return ExamAnswerUtils.checkCorrectAnswer(ans, listAns, question)
        } else {
            return true
        }
    }

    override suspend fun getLastPendingSubmission(userId: String?): RealmSubmission? {
        return databaseService.withRealm { realm ->
            realm.where(RealmSubmission::class.java)
                .equalTo("status", "pending")
                .equalTo("userId", userId)
                .findAll().lastOrNull()?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun updateSubmissionStatus(submissionId: String?, status: String) {
        if (submissionId.isNullOrEmpty()) return
        update(RealmSubmission::class.java, "id", submissionId) { submission ->
            submission.status = status
        }
    }

    override suspend fun getExamByStepId(stepId: String): RealmStepExam? {
        return findByField(RealmStepExam::class.java, "stepId", stepId)
    }

    override suspend fun getExamById(id: String): RealmStepExam? {
        return findByField(RealmStepExam::class.java, "id", id)
    }
}
