package org.ole.planet.myplanet.repository

import android.content.Context
import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext

import java.io.File
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import org.ole.planet.myplanet.data.room.dao.SubmitPhotosDao
import org.ole.planet.myplanet.data.room.dao.AnswerDao
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.data.room.dao.SubmissionDao
import org.ole.planet.myplanet.data.room.dao.UserDao
import org.ole.planet.myplanet.model.CreateExamSubmissionRequest
import org.ole.planet.myplanet.model.ExamAnswerData
import org.ole.planet.myplanet.model.QuestionAnswer
import org.ole.planet.myplanet.model.Answer
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.MembershipDoc
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.SubmitPhotos
import org.ole.planet.myplanet.model.TeamReference
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.model.SubmissionDetail
import org.ole.planet.myplanet.model.SubmissionItem
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.ExamAnswerUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils

class SubmissionsRepositoryImpl @Inject internal constructor(
    private val teamsRepositoryProvider: Provider<TeamsRepository>,
    private val surveysRepositoryProvider: Provider<SurveysRepository>,
    @ApplicationContext private val context: Context,
    private val sharedPrefManager: SharedPrefManager,
    private val exporter: SubmissionsRepositoryExporter,
    private val submitPhotosDao: SubmitPhotosDao,
    private val submissionDao: SubmissionDao,
    private val answerDao: AnswerDao,
    private val examDao: ExamDao,
    private val questionDao: QuestionDao,
    private val userDao: UserDao
) : SubmissionsRepository {

    override suspend fun generateSubmissionPdf(submissionId: String): File? {
        return exporter.generateSubmissionPdf(context, submissionId)
    }

    override suspend fun generateMultipleSubmissionsPdf(submissionIds: List<String>, examTitle: String): File? {
        return exporter.generateMultipleSubmissionsPdf(context, submissionIds, examTitle)
    }

    private fun Submission.examIdFromParentId(): String? {
        return parentId?.substringBefore("@")
    }

    private suspend fun hydrateSubmissions(rows: List<Submission>): List<Submission> {
        if (rows.isEmpty()) return emptyList()
        val answersBySubmissionId = answerDao.getBySubmissionIds(rows.map { it.id }).groupBy { it.submissionId }
        return rows.map { row -> row.apply { answers = answersBySubmissionId[id].orEmpty().toMutableList(); teamId?.let { membershipDoc = MembershipDoc().apply { this.teamId = it } } } }
    }

    private suspend fun hydrateSubmission(row: Submission?): Submission? {
        return row?.let { hydrateSubmissions(listOf(it)).firstOrNull() }
    }

    override fun getPendingSurveysFlow(userId: String?): Flow<List<Submission>> {
        return submissionDao.observePendingSurveys(userId).map { rows -> rows.map { it } }
    }

    override fun getSubmissionsFlow(userId: String): Flow<List<Submission>> {
        return submissionDao.observeByUserId(userId).map { rows -> rows.map { it } }.distinctUntilChanged { old, new ->
            // Assuming any meaningful mutation bumps lastUpdateTime.
            old.size == new.size && old.zip(new).all { (o, n) -> o.id == n.id && o.lastUpdateTime == n.lastUpdateTime }
        }
    }

    override suspend fun getPendingSurveys(userId: String?): List<Submission> {
        if (userId == null) return emptyList()
        return hydrateSubmissions(submissionDao.getPendingSurveys(userId))
    }

    private suspend fun getExamsByIds(examIds: List<String>): List<StepExam> {
        if (examIds.isEmpty()) return emptyList()
        return examDao.getByIds(examIds).map { it }
    }

    override suspend fun getUniquePendingSurveys(userId: String?): List<Submission> {
        if (userId == null) return emptyList()

        val pendingSurveys = hydrateSubmissions(submissionDao.getUniquePendingSurveyCandidates(userId))

        if (pendingSurveys.isEmpty()) {
            return emptyList()
        }

        val examIds = pendingSurveys.mapNotNull { it.examIdFromParentId() }.distinct()
        if (examIds.isEmpty()) {
            return emptyList()
        }

        val exams = getExamsByIds(examIds)
        val validExamIds = exams.mapNotNull { it.id }.toSet()

        val uniqueSurveys = linkedMapOf<String, Submission>()
        pendingSurveys.forEach { submission ->
            val examId = submission.examIdFromParentId()
            if (examId != null && validExamIds.contains(examId) && !uniqueSurveys.containsKey(examId)) {
                uniqueSurveys[examId] = submission
            }
        }
        return uniqueSurveys.values.toList()
    }

    override suspend fun getSurveyTitlesFromSubmissions(
        submissions: List<Submission>
    ): List<String> {
        val examIds = submissions.mapNotNull { it.examIdFromParentId() }
        if (examIds.isEmpty()) {
            return emptyList()
        }

        val exams = getExamsByIds(examIds)
        val examMap = exams.associate { it.id to (it.name ?: "") }

        return submissions.map { submission ->
            val examId = submission.examIdFromParentId()
            examMap[examId] ?: ""
        }
    }

    override suspend fun getExamMap(
        submissions: List<Submission>
    ): Map<String?, StepExam> {
        val examIds = submissions.mapNotNull { it.examIdFromParentId() }.distinct()
        if (examIds.isEmpty()) {
            return emptyMap()
        }

        val exams = getExamsByIds(examIds)
        val examMap = exams.associateBy { it.id }

        return submissions.mapNotNull { sub ->
            val parentId = sub.parentId
            val examId = sub.examIdFromParentId()
            examMap[examId]?.let { parentId to it }
        }.toMap()
    }

    override suspend fun getExamQuestionCount(stepId: String): Int {
        return examDao.getFirstByStepId(stepId)?.noOfQuestions ?: 0
    }

    override suspend fun getSubmissionById(id: String): Submission? {
        return hydrateSubmission(submissionDao.getByIdOrRemoteId(id))
    }

    override suspend fun getSubmissionsByIds(ids: List<String>): List<Submission> {
        if (ids.isEmpty()) return emptyList()

        return hydrateSubmissions(submissionDao.getByIds(ids))
    }

    override suspend fun getSubmissionsByUserId(userId: String): List<Submission> {
        return hydrateSubmissions(submissionDao.getByUserId(userId))
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

        if (questionDao.countByExamId(stepExamId) == 0) {
            return false
        }

        val parentId = "$stepExamId@$courseId"
        return submissionDao.countByUserParentAndType(userId, parentId, type) > 0
    }

    override suspend fun hasPendingOfflineSubmissions(): Boolean {
        return submissionDao.countPendingOfflineSubmissions() > 0
    }

    override suspend fun hasPendingExamResults(): Boolean {
        return submissionDao.countPendingExamResults() > 0
    }

    override suspend fun createBulkSurveySubmissions(examId: String, userIds: List<String>) {
        val courseId = examDao.getById(examId)?.courseId
        val parentId = if (!courseId.isNullOrEmpty()) {
            "$examId@$courseId"
        } else {
            examId
        }
        userIds.forEach { userId ->
            getOrCreateSubmission(userId, parentId)
        }
    }

    override suspend fun saveSubmission(submission: Submission) {
        val submissionEntity = submission ?: return
        val answerEntities = submission.answers?.mapNotNull { it }.orEmpty()
        submissionDao.upsertAll(listOf(submissionEntity))
        if (answerEntities.isNotEmpty()) {
            answerDao.upsertAll(answerEntities)
        }
    }

    override suspend fun markSubmissionComplete(id: String, payload: JsonObject) {
        submissionDao.markComplete(id, payload.toString())
    }

    override suspend fun getSubmissionDetail(submissionId: String): SubmissionDetail? {
        var submission = hydrateSubmission(submissionDao.getByIdOrRemoteId(submissionId))

        if (submission == null) {
            submission = hydrateSubmission(submissionDao.getFirstByParentIdContaining(submissionId))
        }

        if (submission == null) {
            return null
        }

        val examId = submission.parentId?.substringBefore('@')
        val exam = examId?.let { getExamById(it) }

        val user = submission.userId?.let { userDao.getById(it) }

        val questions = examId?.let { questionDao.getByExamId(it).map { question -> question } } ?: emptyList()

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
                            formattedAnswer = choices.joinToString(", ") { choiceId ->
                                try {
                                    val choicesArray = JsonParser.parseString(question.choices).asJsonArray
                                    val choiceObject = choicesArray.find {
                                        it.isJsonObject && it.asJsonObject.has("id") && it.asJsonObject.get(
                                            "id"
                                        ).asString == choiceId
                                    }?.asJsonObject
                                    choiceObject?.get("text")?.asString ?: choiceId
                                } catch (_: Exception) {
                                    choiceId
                                }
                            }
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

        return SubmissionDetail(
            title = exam?.name ?: "Submission Details",
            status = "Status: ${submission.status ?: "Unknown"}",
            date = submission.startTime,
            submittedBy = "Submitted by: ${user?.name ?: "Unknown"}",
            questionAnswers = questionAnswers
        )
    }

    override fun getNormalizedSubmitterName(submission: Submission): String? {
        return runCatching {
            submission.user?.takeIf { it.isNotBlank() }?.let { userJson ->
                val jsonObject = JsonParser.parseString(userJson).asJsonObject
                JsonUtils.getString("name", jsonObject).takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    override suspend fun getSubmissionsByParentId(parentId: String?, userId: String?, status: String?): List<Submission> {
        return hydrateSubmissions(submissionDao.getByParentUserAndStatus(parentId, userId, status))
    }

    override suspend fun getSubmissionItems(parentId: String?, userId: String?): List<SubmissionItem> {
        return submissionDao.getByParentUserAndStatus(parentId, userId, null).map {
            SubmissionItem(
                id = it.id,
                lastUpdateTime = it.lastUpdateTime,
                status = it.status ?: "",
                uploaded = it.uploaded
            )
        }
    }

    override suspend fun deleteExamSubmissions(examId: String, courseId: String?, userId: String?) {
        val parentIdToSearch = if (!courseId.isNullOrEmpty()) {
            "${examId}@${courseId}"
        } else {
            examId
        }
        val submissions = submissionDao.getByParentUserAndStatus(parentIdToSearch, userId, null)
        val submissionIds = submissions.map { it.id }
        if (submissionIds.isNotEmpty()) {
            answerDao.deleteBySubmissionIds(submissionIds)
        }
        submissionDao.deleteByParentAndUser(parentIdToSearch, userId)
    }

    override suspend fun isStepCompleted(stepId: String?, userId: String?): Boolean {
        if (stepId == null) return true
        val exam = examDao.getFirstByStepId(stepId) ?: return true
        return exam.id?.let {
            submissionDao.countCompletedByUserAndExamId(userId, it) > 0
        } ?: false
    }

    private suspend fun getSurveysByCourseId(courseId: String): List<StepExam> {
        return examDao.getByCourseIdAndType(courseId, "survey").map { it }
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

    override suspend fun hasPendingSurvey(courseId: String, userId: String?): Boolean {
        val surveys = getSurveysByCourseId(courseId)
        for (survey in surveys) {
            if (!hasSubmission(survey.id, survey.courseId, userId, "survey")) {
                return true
            }
        }
        return false
    }

    override suspend fun addSubmissionPhoto(
        submissionId: String?,
        examId: String?,
        courseId: String?,
        memberId: String?,
        photoPath: String?
    ) {
        submitPhotosDao.insert(
            SubmitPhotos().apply {
                id = UUID.randomUUID().toString()
                this.submissionId = submissionId
                this.examId = examId
                this.courseId = courseId
                this.memberId = memberId
                date = Date().toString()
                uniqueId = NetworkUtils.getUniqueIdentifier()
                photoLocation = photoPath
                uploaded = false
            }
        )
    }

    override suspend fun createExamSubmission(request: CreateExamSubmissionRequest): Submission? {
        val (userId, userDob, userGender, exam, type, teamId) = request
        val team = if (!teamId.isNullOrEmpty()) {
            teamsRepositoryProvider.get().getTeamById(teamId)
        } else {
            null
        }

        val now = Date().time
        val submission = Submission().apply {
            id = UUID.randomUUID().toString()
            parentId = when {
                !exam.id.isNullOrEmpty() -> if (!exam.courseId.isNullOrEmpty()) {
                    "${exam.id}@${exam.courseId}"
                } else {
                    exam.id
                }
                else -> null
            }
            parent = JsonObject().apply {
                addProperty("_id", exam.id ?: "")
                addProperty("name", exam.name ?: "")
                addProperty("courseId", exam.courseId ?: "")
                addProperty("sourcePlanet", exam.sourcePlanet ?: "")
                addProperty("teamShareAllowed", exam.isTeamShareAllowed)
                addProperty("noOfQuestions", exam.noOfQuestions)
                addProperty("isFromNation", exam.isFromNation)
            }.toString()
            this.userId = userId
            status = "pending"
            this.type = type
            startTime = now
            lastUpdateTime = now
            answers = mutableListOf()

            if (team != null) {
                teamObject = TeamReference().apply {
                    _id = team._id
                    name = team.name
                    this.type = team.type ?: "team"
                }
                membershipDoc = MembershipDoc().apply { this.teamId = teamId }
                user = JsonObject().apply {
                    addProperty("age", userDob ?: "")
                    addProperty("gender", userGender ?: "")
                    add("membershipDoc", JsonObject().apply { addProperty("teamId", teamId) })
                }.toString()
            }
        }
        saveSubmission(submission)
        return submission
    }

    override suspend fun saveExamAnswer(answerData: ExamAnswerData): Boolean {
        val (submission, question, ans, listAns, otherText, otherVisible, type, index, total, isExplicitSubmission) = answerData
        val submissionRow = submission?.id?.let { submissionDao.getByIdOrRemoteId(it) }
            ?: submission
            ?: submissionDao.getLatestPendingByUser(submission?.userId)
        val submissionId = submissionRow?.id
        val questionId = question.id

        if (submissionRow != null && !questionId.isNullOrBlank()) {
            val existing = answerDao.getBySubmissionAndQuestion(submissionRow.id, questionId)
            val valueChoices: List<String>?
            val value: String?

            if (question.type.equals("select", ignoreCase = true)) {
                if (otherVisible && !otherText.isNullOrEmpty()) {
                    value = otherText
                    valueChoices = listOf("""{"id":"other","text":"$otherText"}""")
                } else {
                    val choiceText = ExamAnswerUtils.getChoiceTextById(question, ans)
                    value = choiceText
                    valueChoices = if (ans.isNotEmpty()) {
                        listOf("""{"id":"$ans","text":"$choiceText"}""")
                    } else {
                        emptyList()
                    }
                }
            } else if (question.type.equals("selectMultiple", ignoreCase = true)) {
                value = ""
                valueChoices = listAns?.map { (text, id) ->
                    if (id == "other" && otherVisible && !otherText.isNullOrEmpty()) {
                        """{"id":"other","text":"$otherText"}"""
                    } else {
                        """{"id":"$id","text":"$text"}"""
                    }
                }
            } else {
                value = if (otherVisible && !otherText.isNullOrEmpty()) otherText else ans
                valueChoices = null
            }

            val isCorrect = if (type == "exam") {
                ExamAnswerUtils.checkCorrectAnswer(ans, listAns, question)
            } else {
                true
            }
            val isFinal = index == total - 1
            val newStatus = when {
                isFinal && isExplicitSubmission && type == "survey" -> "complete"
                isFinal && isExplicitSubmission -> "requires grading"
                else -> "pending"
            }
            val now = Date().time
            val answer = Answer(
                id = existing?.id ?: UUID.randomUUID().toString(),
                value = value,
                valueChoices = valueChoices,
                mistakes = if (type == "exam" && !isCorrect) (existing?.mistakes ?: 0) + 1 else existing?.mistakes ?: 0,
                isPassed = if (type == "exam") isCorrect else existing?.isPassed ?: false,
                grade = if (type == "exam") 1 else existing?.grade ?: 0,
                examId = question.examId,
                questionId = questionId,
                submissionId = submissionId,
            )
            answerDao.upsertAll(listOf(answer))
            submissionDao.updateStatusAndLastUpdate(submissionRow.id, newStatus, now)

            if (newStatus == "complete" && type == "survey") {
                submissionDao.deletePendingSurveyOrphans(submissionRow.parentId, submissionRow.userId)
            }
        }

        return if (type == "exam") {
            ExamAnswerUtils.checkCorrectAnswer(ans, listAns, question)
        } else {
            true
        }
    }

    override suspend fun getLastPendingSubmission(userId: String?): Submission? {
        return hydrateSubmission(submissionDao.getLatestPendingByUser(userId))
    }

    override suspend fun updateSubmissionStatus(submissionId: String?, status: String) {
        if (submissionId.isNullOrEmpty()) return
        submissionDao.updateStatus(submissionId, status)
    }

    override suspend fun getExamByStepId(stepId: String): StepExam? {
        return examDao.getFirstByStepId(stepId)
    }

    override suspend fun getExamById(id: String): StepExam? {
        return examDao.getById(id)
    }

    override suspend fun getUnuploadedPhotos(): List<Pair<String?, JsonObject>> {
        return submitPhotosDao.getUnuploaded().map { photo ->
            Pair(photo.id, SubmitPhotos.serialize(photo))
        }
    }

    override suspend fun markPhotoUploaded(photoId: String?, rev: String, id: String) {
        photoId?.let { submitPhotosDao.markUploaded(it, rev, id) }
    }

    override suspend fun getPhotosByIds(ids: Array<String>): List<SubmitPhotos> {
        if (ids.isEmpty()) return emptyList()
        return submitPhotosDao.getByIds(ids)
    }

    override suspend fun getOrCreateSubmission(userId: String?, parentId: String): Submission {
        val existing = hydrateSubmission(submissionDao.getLatestPendingByUserAndParent(userId, parentId))
        if (existing != null) {
            return existing
        }

        val submission = Submission().apply {
            id = UUID.randomUUID().toString()
            this.userId = userId
            this.parentId = parentId
            status = "pending"
            type = "survey"
            startTime = Date().time
            lastUpdateTime = startTime
            answers = mutableListOf()
        }
        saveSubmission(submission)
        return submission
    }

    override suspend fun bulkInsertFromSync(jsonArray: JsonArray) {
        val documentList = ArrayList<JsonObject>(jsonArray.size())
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        upsertRoomSubmissionsFromSync(documentList)
    }

    override suspend fun insertSubmission(submission: JsonObject) {
        if (submission.has("_attachments")) return
        upsertRoomSubmissionsFromSync(listOf(submission))
    }

    private fun upsertRoomSubmissionsFromSync(documentList: List<JsonObject>) {
        val submissions = ArrayList<Submission>(documentList.size)
        val answers = ArrayList<Answer>()

        documentList.filterNot { it.has("_attachments") }.forEach { submission ->
            val id = JsonUtils.getString("_id", submission)
            if (id.isBlank()) return@forEach
            val serverStatus = JsonUtils.getString("status", submission)
            val userJson = JsonUtils.getJsonObject("user", submission)
            val teamJson = JsonUtils.getJsonObject("team", submission)
            val membershipJson = JsonUtils.getJsonObject("membershipDoc", userJson)
            val userId = normalizeSubmissionUserId(JsonUtils.getString("_id", userJson))
            submissions.add(
                Submission(
                    id = id,
                    _id = id,
                    _rev = JsonUtils.getString("_rev", submission),
                    parentId = JsonUtils.getString("parentId", submission),
                    type = JsonUtils.getString("type", submission),
                    userId = userId,
                    user = JsonUtils.gson.toJson(userJson),
                    startTime = JsonUtils.getLong("startTime", submission),
                    lastUpdateTime = JsonUtils.getLong("lastUpdateTime", submission),
                    grade = JsonUtils.getLong("grade", submission),
                    status = serverStatus,
                    uploaded = JsonUtils.getString("_rev", submission).isNotEmpty(),
                    sender = JsonUtils.getString("sender", submission),
                    source = JsonUtils.getString("source", submission),
                    parentCode = JsonUtils.getString("parentCode", submission),
                    parent = JsonUtils.gson.toJson(JsonUtils.getJsonObject("parent", submission)),
                    teamId = JsonUtils.getString("_id", teamJson).ifBlank { JsonUtils.getString("teamId", membershipJson) },
                    isUpdated = false,
                )
            )

            val answersArray = JsonUtils.getJsonArray("answers", submission)
            for (i in 0 until answersArray.size()) {
                val answerJson = answersArray[i].asJsonObject
                val valueElement = answerJson.get("value")
                val valueChoices = if (valueElement != null && valueElement.isJsonArray) {
                    valueElement.asJsonArray.map { it.toString() }
                } else {
                    null
                }
                val examIdPart = JsonUtils.getString("parentId", submission).split("@").firstOrNull()
                    ?: JsonUtils.getString("parentId", submission)
                answers.add(
                    Answer(
                        id = "$id-$i",
                        value = valueElement?.takeIf { !it.isJsonArray && !it.isJsonNull }?.asString,
                        valueChoices = valueChoices,
                        mistakes = JsonUtils.getInt("mistakes", answerJson),
                        isPassed = JsonUtils.getBoolean("passed", answerJson),
                        examId = JsonUtils.getString("parentId", submission),
                        questionId = JsonUtils.getString("questionId", answerJson).ifBlank { "$examIdPart-$i" },
                        submissionId = id,
                    )
                )
            }
        }

        if (submissions.isEmpty() && answers.isEmpty()) return

        if (submissions.isNotEmpty()) submissionDao.upsertAllBlocking(submissions)
        if (answers.isNotEmpty()) answerDao.upsertAllBlocking(answers)
    }

    private fun normalizeSubmissionUserId(userId: String): String {
        return if (userId.contains("@")) {
            val localUserId = userId.substringBefore("@")
            if (localUserId.startsWith("org.couchdb.user:")) localUserId else "org.couchdb.user:$localUserId"
        } else {
            userId
        }
    }

    private data class PayloadData(
        val user: UserEntity?,
        val exam: StepExam?,
        val questions: List<ExamQuestion>
    )

    private suspend fun getPayloadData(submission: Submission): PayloadData {
        val user = submission.userId?.let { userDao.getById(it) }
        val examId = submission.examIdFromParentId()
        val exam = examId?.let { examDao.getById(it) }
        val questions = exam?.id?.let { questionDao.getByExamId(it).map { question -> question } } ?: emptyList()
        return PayloadData(user, exam, questions)
    }

    override suspend fun getExamUploadPayload(submission: Submission): JsonObject {
        val `object` = JsonObject()
        val payloadData = getPayloadData(submission)
        val user = payloadData.user
        val exam = payloadData.exam

        if (!TextUtils.isEmpty(submission._id)) {
            `object`.addProperty("_id", submission._id)
        }
        if (!TextUtils.isEmpty(submission._rev)) {
            `object`.addProperty("_rev", submission._rev)
        }
        `object`.addProperty("parentId", submission.parentId)
        `object`.addProperty("type", submission.type)

        if (submission.teamObject != null) {
            val teamJson = JsonObject()
            teamJson.addProperty("_id", submission.teamObject?._id)
            teamJson.addProperty("name", submission.teamObject?.name)
            teamJson.addProperty("type", submission.teamObject?.type)
            `object`.add("team", teamJson)
        }

        `object`.addProperty("grade", submission.grade)
        `object`.addProperty("startTime", submission.startTime)
        `object`.addProperty("lastUpdateTime", submission.lastUpdateTime)
        `object`.addProperty("status", submission.status)
        `object`.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
        `object`.addProperty("deviceName", NetworkUtils.getDeviceName())
        `object`.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
        `object`.addProperty("sender", submission.sender)
        `object`.addProperty("source", sharedPrefManager.getPlanetCode())
        `object`.addProperty("parentCode", sharedPrefManager.getParentCode())
        `object`.add("answers", Answer.serializeRealmAnswer(submission.answers ?: mutableListOf()))
        if (exam != null) {
            `object`.add("parent", StepExam.serializeExam(exam, payloadData.questions))
        } else {
            val parent = JsonUtils.gson.fromJson(submission.parent, JsonObject::class.java)
            `object`.add("parent", parent)
        }
        if (TextUtils.isEmpty(submission.user)) {
            `object`.add("user", user?.serialize())
        } else {
            `object`.add("user", JsonParser.parseString(submission.user))
        }
        return `object`
    }

    override suspend fun serializeSubmission(submission: Submission, context: Context, source: String, parentCode: String): JsonObject {
        val jsonObject = JsonObject()

        try {
            val payloadData = getPayloadData(submission)
            val exam = payloadData.exam

            if (!submission._id.isNullOrEmpty()) {
                jsonObject.addProperty("_id", submission._id)
            }
            if (!submission._rev.isNullOrEmpty()) {
                jsonObject.addProperty("_rev", submission._rev)
            }

            jsonObject.addProperty("parentId", submission.parentId ?: "")
            jsonObject.addProperty("type", submission.type ?: "survey")
            jsonObject.addProperty("grade", submission.grade)
            jsonObject.addProperty("startTime", submission.startTime)
            jsonObject.addProperty("lastUpdateTime", submission.lastUpdateTime)
            jsonObject.addProperty("status", submission.status ?: "pending")
            jsonObject.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            jsonObject.addProperty("deviceName", NetworkUtils.getDeviceName())
            jsonObject.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
            jsonObject.addProperty("sender", submission.sender)
            jsonObject.addProperty("source", source)
            jsonObject.addProperty("parentCode", parentCode)
            jsonObject.add("answers", Answer.serializeRealmAnswer(submission.answers ?: mutableListOf()))
            if (exam != null) {
                jsonObject.add("parent", StepExam.serializeExam(exam, payloadData.questions))
            } else if (!submission.parent.isNullOrEmpty()) {
                jsonObject.add("parent", JsonParser.parseString(submission.parent))
            }

            if (!submission.user.isNullOrEmpty()) {
                val userJson = JsonParser.parseString(submission.user).asJsonObject
                if (submission.membershipDoc != null) {
                    val membershipJson = JsonObject()
                    membershipJson.addProperty("teamId", submission.membershipDoc?.teamId ?: "")
                    userJson.add("membershipDoc", membershipJson)
                }
                jsonObject.add("user", userJson)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return jsonObject
    }

    override suspend fun getPendingExamResults(): List<Submission> {
        return submissionDao.getPendingExamResults().map { entity ->
            val answers = answerDao.getBySubmissionId(entity.id)
            entity.apply { this.answers = answers.toMutableList(); teamId?.let { membershipDoc = MembershipDoc().apply { this.teamId = it } } }
        }
    }

    override suspend fun getPendingSubmissionsForUpload(): List<Submission> {
        return submissionDao.getPendingSubmissions().map { entity ->
            val answers = answerDao.getBySubmissionId(entity.id)
            entity.apply { this.answers = answers.toMutableList(); teamId?.let { membershipDoc = MembershipDoc().apply { this.teamId = it } } }
        }
    }
}
