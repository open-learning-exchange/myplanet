package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.realm.Case
import io.realm.RealmList
import io.realm.Sort
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.QuestionAnswer
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMembershipDoc
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmTeamReference
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.SubmissionDetail
import org.ole.planet.myplanet.model.SubmissionItem
import org.ole.planet.myplanet.utils.ExamAnswerUtils
import org.ole.planet.myplanet.utils.NetworkUtils

class SubmissionsRepositoryImpl @Inject internal constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val teamsRepositoryProvider: Provider<TeamsRepository>,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val sharedPrefManager: org.ole.planet.myplanet.services.SharedPrefManager
) : RealmRepository(databaseService, realmDispatcher), SubmissionsRepository {

    private fun RealmSubmission.examIdFromParentId(): String? {
        return parentId?.substringBefore("@")
    }

    override fun getPendingSurveysFlow(userId: String?): Flow<List<RealmSubmission>> {
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

private suspend fun getExamsByIds(examIds: List<String>): List<RealmStepExam> {
        if (examIds.isEmpty()) return emptyList()
        return queryList(RealmStepExam::class.java) {
            `in`("id", examIds.toTypedArray())
        }
    }

    override suspend fun getUniquePendingSurveys(userId: String?): List<RealmSubmission> {
        if (userId == null) return emptyList()

        val pendingSurveys = queryList(RealmSubmission::class.java) {
            equalTo("userId", userId)
            equalTo("status", "pending")
            equalTo("type", "survey")
            isNull("membershipDoc")
        }

        if (pendingSurveys.isEmpty()) {
            return emptyList()
        }

        val examIds = pendingSurveys.mapNotNull { it.examIdFromParentId() }.distinct()
        if (examIds.isEmpty()) {
            return emptyList()
        }

        val exams = getExamsByIds(examIds)
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

        val exams = getExamsByIds(examIds)
        val examMap = exams.associate { it.id to (it.name ?: "") }

        return submissions.map { submission ->
            val examId = submission.examIdFromParentId()
            examMap[examId] ?: ""
        }
    }

    override suspend fun getExamMap(
        submissions: List<RealmSubmission>
    ): Map<String?, RealmStepExam> {
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
        val parentId = withRealm { realm ->
            val courseId = realm.where(RealmStepExam::class.java).equalTo("id", examId).findFirst()?.courseId
            if (!courseId.isNullOrEmpty()) {
                examId + "@" + courseId
            } else {
                examId
            }
        }
        getOrCreateSubmission(userId, parentId)
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
            sub.isUpdated = true // Mark for upload
        }
    }

    override suspend fun getSubmissionDetail(submissionId: String): SubmissionDetail? {
        var submission = queryList(RealmSubmission::class.java) {
            equalTo("id", submissionId)
                .or()
                .equalTo("_id", submissionId)
        }.firstOrNull()

        if (submission == null) {
            submission = queryList(RealmSubmission::class.java) {
                contains("parentId", submissionId)
            }.firstOrNull()
        }

        if (submission == null) {
            return null
        }

        val examId = submission.parentId?.substringBefore('@')
        val exam = examId?.let { getExamById(it) }

        val user = submission.userId?.let { findByField(RealmUser::class.java, "id", it) }

        val questions = queryList(RealmExamQuestion::class.java) {
            equalTo("examId", examId)
        }

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

        return SubmissionDetail(
            title = exam?.name ?: "Submission Details",
            status = "Status: ${submission.status ?: "Unknown"}",
            date = submission.startTime,
            submittedBy = "Submitted by: ${user?.name ?: "Unknown"}",
            questionAnswers = questionAnswers
        )
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

        var detachedSub: RealmSubmission? = null
        executeTransaction { r ->
                val managedSub = createSubmissionInternal(null, r)

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
        return detachedSub
    }

    override suspend fun saveExamAnswer(answerData: org.ole.planet.myplanet.model.ExamAnswerData): Boolean {
        val (submission, question, ans, listAns, otherText, otherVisible, type, index, total, isExplicitSubmission) = answerData
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

                if (realmSubmission.status == "complete" && type == "survey") {
                    val orphans = r.where(RealmSubmission::class.java)
                        .equalTo("parentId", realmSubmission.parentId)
                        .equalTo("userId", realmSubmission.userId)
                        .equalTo("status", "pending")
                        .equalTo("type", "survey")
                        .isNull("membershipDoc")
                        .findAll()
                    orphans.deleteAllFromRealm()
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
        return withRealm { realm ->
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

    override suspend fun getUnuploadedPhotos(): List<Pair<String?, JsonObject>> {
        return withRealm { realm ->
            val data = realm.where(RealmSubmitPhotos::class.java).equalTo("uploaded", false).findAll()
            if (data.isEmpty()) {
                emptyList()
            } else {
                data.map { photo ->
                    Pair(photo.id, RealmSubmitPhotos.serializeRealmSubmitPhotos(photo))
                }
            }
        }
    }

    override suspend fun markPhotoUploaded(photoId: String?, rev: String, id: String) {
        executeTransaction { transactionRealm ->
            transactionRealm.where(RealmSubmitPhotos::class.java)
                .equalTo("id", photoId)
                .findFirst()?.let { sub ->
                    sub.uploaded = true
                    sub._rev = rev
                    sub._id = id
                }
        }
    }

    override suspend fun getPhotosByIds(ids: Array<String>): List<RealmSubmitPhotos> {
        if (ids.isEmpty()) return emptyList()
        return withRealm { realm ->
            val results = realm.where(RealmSubmitPhotos::class.java)
                .`in`("id", ids)
                .findAll()
            realm.copyFromRealm(results)
        }
    }

    override suspend fun getOrCreateSubmission(userId: String?, parentId: String): RealmSubmission {
        var detachedSub: RealmSubmission? = null
        executeTransaction { r ->
            val sub = r.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .equalTo("parentId", parentId)
                .sort("lastUpdateTime", Sort.DESCENDING)
                .equalTo("status", "pending")
                .findFirst()

            val managedSub = createSubmissionInternal(sub, r)
            if (managedSub.userId.isNullOrEmpty()) managedSub.userId = userId
            if (managedSub.parentId.isNullOrEmpty()) managedSub.parentId = parentId
            if (managedSub.status.isNullOrEmpty()) managedSub.status = "pending"
            if (managedSub.type.isNullOrEmpty()) managedSub.type = "survey"
            if (managedSub.startTime == 0L) managedSub.startTime = Date().time

            detachedSub = r.copyFromRealm(managedSub)
        }
        return detachedSub!!
    }

    private fun createSubmissionInternal(sub: RealmSubmission?, mRealm: io.realm.Realm): RealmSubmission {
        var submission = sub
        if (submission == null || (submission.status == "complete" && (submission.type == "exam" || submission.type == "survey"))) {
            submission = mRealm.createObject(RealmSubmission::class.java, UUID.randomUUID().toString())
        }
        submission!!.lastUpdateTime = Date().time
        return submission
    }

    override fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {
        val documentList = ArrayList<com.google.gson.JsonObject>(jsonArray.size())
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("doc", jsonDoc)
            val id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        documentList.forEach { jsonDoc ->
            insertSubmission(realm, jsonDoc)
        }
    }

    override fun insertSubmission(mRealm: io.realm.Realm, submission: JsonObject) {
        if (submission.has("_attachments")) {
            return
        }

        val id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", submission)
        var transactionStarted = false

        try {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
                transactionStarted = true
            }

            var sub = mRealm.where(RealmSubmission::class.java).equalTo("_id", id).findFirst()
            val isNewSubmission = sub == null
            val hadLocalChanges = !isNewSubmission && sub.isUpdated
            val serverStatus = org.ole.planet.myplanet.utils.JsonUtils.getString("status", submission)
            val isStatusDowngrade = !isNewSubmission && serverStatus == "pending" &&
                (sub.status == "complete" || sub.status == "requires grading")
            val skipOverwrite = hadLocalChanges || isStatusDowngrade

            if (sub == null) {
                sub = mRealm.createObject(RealmSubmission::class.java, id)
            }

            updateBasicFields(sub, id, serverStatus, skipOverwrite, submission)
            updateTeam(mRealm, sub, submission)
            updateMembership(mRealm, sub, submission)
            updateUserId(sub, submission)
            updateAnswers(mRealm, sub, submission, skipOverwrite)

            if (transactionStarted) {
                mRealm.commitTransaction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (transactionStarted && mRealm.isInTransaction) {
                mRealm.cancelTransaction()
            }
        }
    }

    private fun updateBasicFields(
        sub: RealmSubmission?,
        id: String,
        serverStatus: String,
        skipOverwrite: Boolean,
        submission: JsonObject
    ) {
        sub?._id = id
        if (!skipOverwrite) {
            sub?.status = serverStatus
            sub?.isUpdated = false
        }
        sub?._rev = org.ole.planet.myplanet.utils.JsonUtils.getString("_rev", submission)
        sub?.grade = org.ole.planet.myplanet.utils.JsonUtils.getLong("grade", submission)
        sub?.type = org.ole.planet.myplanet.utils.JsonUtils.getString("type", submission)
        sub?.uploaded = org.ole.planet.myplanet.utils.JsonUtils.getString("_rev", submission).isNotEmpty()
        sub?.startTime = org.ole.planet.myplanet.utils.JsonUtils.getLong("startTime", submission)
        sub?.lastUpdateTime = org.ole.planet.myplanet.utils.JsonUtils.getLong("lastUpdateTime", submission)
        sub?.parentId = org.ole.planet.myplanet.utils.JsonUtils.getString("parentId", submission)
        sub?.sender = org.ole.planet.myplanet.utils.JsonUtils.getString("sender", submission)
        sub?.source = org.ole.planet.myplanet.utils.JsonUtils.getString("source", submission)
        sub?.parentCode = org.ole.planet.myplanet.utils.JsonUtils.getString("parentCode", submission)
        sub?.parent = org.ole.planet.myplanet.utils.JsonUtils.gson.toJson(org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("parent", submission))
        sub?.user = org.ole.planet.myplanet.utils.JsonUtils.gson.toJson(org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("user", submission))
    }

    private fun updateTeam(mRealm: io.realm.Realm, sub: RealmSubmission?, submission: JsonObject) {
        if (submission.has("team") && submission.get("team").isJsonObject) {
            val teamJson = submission.getAsJsonObject("team")
            val teamRef = mRealm.createObject(org.ole.planet.myplanet.model.RealmTeamReference::class.java)
            teamRef._id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", teamJson)
            teamRef.name = org.ole.planet.myplanet.utils.JsonUtils.getString("name", teamJson)
            teamRef.type = org.ole.planet.myplanet.utils.JsonUtils.getString("type", teamJson)
            sub?.teamObject = teamRef
        }
    }

    private fun updateMembership(mRealm: io.realm.Realm, sub: RealmSubmission?, submission: JsonObject) {
        val userJson = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("user", submission)
        if (userJson.has("membershipDoc")) {
            val membershipJson = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("membershipDoc", userJson)
            if (membershipJson.entrySet().isNotEmpty()) {
                val membership = mRealm.createObject(RealmMembershipDoc::class.java)
                membership.teamId = org.ole.planet.myplanet.utils.JsonUtils.getString("teamId", membershipJson)
                sub?.membershipDoc = membership
            }
        }
    }

    private fun updateUserId(sub: RealmSubmission?, submission: JsonObject) {
        val userId = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("user", submission))
        sub?.userId = if (userId.contains("@")) {
            val us = userId.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (us[0].startsWith("org.couchdb.user:")) us[0] else "org.couchdb.user:${us[0]}"
        } else {
            userId
        }
    }

    private fun updateAnswers(
        mRealm: io.realm.Realm,
        sub: RealmSubmission?,
        submission: JsonObject,
        skipOverwrite: Boolean
    ) {
        if (!skipOverwrite && submission.has("answers")) {
            val answersArray = submission.get("answers").asJsonArray
            sub?.answers = io.realm.RealmList<RealmAnswer>()

            val unmanagedAnswers = mutableListOf<RealmAnswer>()
            for (i in 0 until answersArray.size()) {
                val answerJson = answersArray[i].asJsonObject
                val realmAnswer = RealmAnswer()
                realmAnswer.id = UUID.randomUUID().toString()

                realmAnswer.value = org.ole.planet.myplanet.utils.JsonUtils.getString("value", answerJson)
                realmAnswer.mistakes = org.ole.planet.myplanet.utils.JsonUtils.getInt("mistakes", answerJson)
                realmAnswer.isPassed = org.ole.planet.myplanet.utils.JsonUtils.getBoolean("passed", answerJson)
                realmAnswer.submissionId = sub?._id
                realmAnswer.examId = sub?.parentId

                val examIdPart = sub?.parentId?.split("@")?.get(0) ?: sub?.parentId
                realmAnswer.questionId = if (answerJson.has("questionId")) {
                    org.ole.planet.myplanet.utils.JsonUtils.getString("questionId", answerJson)
                } else {
                    "$examIdPart-$i"
                }

                unmanagedAnswers.add(realmAnswer)
            }

            if (unmanagedAnswers.isNotEmpty()) {
                val managedAnswers = mRealm.copyToRealmOrUpdate(unmanagedAnswers)
                sub?.answers?.addAll(managedAnswers)
            }
        }
    }

    override suspend fun getExamUploadPayload(submission: RealmSubmission): JsonObject = databaseService.withRealmAsync { mRealm ->
        val `object` = JsonObject()
        val user = mRealm.where(RealmUser::class.java).equalTo("id", submission.userId).findFirst()
        var examId = submission.parentId
        if (submission.parentId?.contains("@") == true) {
            examId = submission.parentId?.split("@".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()?.get(0)
        }
        val exam = mRealm.where(RealmStepExam::class.java).equalTo("id", examId).findFirst()
        if (!android.text.TextUtils.isEmpty(submission._id)) {
            `object`.addProperty("_id", submission._id)
        }
        if (!android.text.TextUtils.isEmpty(submission._rev)) {
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
        `object`.addProperty("androidId", org.ole.planet.myplanet.utils.NetworkUtils.getUniqueIdentifier())
        `object`.addProperty("deviceName", org.ole.planet.myplanet.utils.NetworkUtils.getDeviceName())
        `object`.addProperty("customDeviceName", org.ole.planet.myplanet.utils.NetworkUtils.getCustomDeviceName(context))
        `object`.addProperty("sender", submission.sender)
        `object`.addProperty("source", sharedPrefManager.getPlanetCode())
        `object`.addProperty("parentCode", sharedPrefManager.getParentCode())
        `object`.add("answers", RealmAnswer.serializeRealmAnswer(submission.answers ?: io.realm.RealmList()))
        if (exam != null) {
            `object`.add("parent", RealmStepExam.serializeExam(mRealm, exam))
        } else {
            val parent = org.ole.planet.myplanet.utils.JsonUtils.gson.fromJson(submission.parent, JsonObject::class.java)
            `object`.add("parent", parent)
        }
        if (android.text.TextUtils.isEmpty(submission.user)) {
            `object`.add("user", user?.serialize())
        } else {
            `object`.add("user", com.google.gson.JsonParser.parseString(submission.user))
        }
        `object`
    }

    override fun serializeSubmission(mRealm: io.realm.Realm, submission: RealmSubmission, context: android.content.Context, source: String, parentCode: String): JsonObject {
        val jsonObject = JsonObject()

        try {
            var examId = submission.parentId
            if (submission.parentId?.contains("@") == true) {
                examId = submission.parentId?.split("@".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()?.get(0)
            }
            val exam = mRealm.where(RealmStepExam::class.java).equalTo("id", examId).findFirst()

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
            jsonObject.addProperty("androidId", org.ole.planet.myplanet.utils.NetworkUtils.getUniqueIdentifier())
            jsonObject.addProperty("deviceName", org.ole.planet.myplanet.utils.NetworkUtils.getDeviceName())
            jsonObject.addProperty("customDeviceName", org.ole.planet.myplanet.utils.NetworkUtils.getCustomDeviceName(context))
            jsonObject.addProperty("sender", submission.sender)
            jsonObject.addProperty("source", source)
            jsonObject.addProperty("parentCode", parentCode)
            jsonObject.add("answers", RealmAnswer.serializeRealmAnswer(submission.answers ?: io.realm.RealmList()))
            if (exam != null) {
                jsonObject.add("parent", RealmStepExam.serializeExam(mRealm, exam))
            } else if (!submission.parent.isNullOrEmpty()) {
                jsonObject.add("parent", com.google.gson.JsonParser.parseString(submission.parent))
            }

            if (!submission.user.isNullOrEmpty()) {
                val userJson = com.google.gson.JsonParser.parseString(submission.user).asJsonObject
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
}
