package org.ole.planet.myplanet.repository

import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import java.util.UUID
import javax.inject.Inject

class ExamRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : ExamRepository {
    private suspend fun <T> withRealm(block: suspend (Realm) -> T): T {
        return withContext(Dispatchers.IO) {
            databaseService.realmInstance.use { realm ->
                block(realm)
            }
        }
    }
    override suspend fun getSubmissionById(id: String): RealmSubmission? {
        return withRealm { realm ->
            val submission = realm.where(RealmSubmission::class.java).equalTo("id", id).findFirst()
            submission?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getExamByStepId(stepId: String): RealmStepExam? {
        return withRealm { realm ->
            val exam = realm.where(RealmStepExam::class.java).equalTo("stepId", stepId).findFirst()
            exam?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getExamById(id: String): RealmStepExam? {
        return withRealm { realm ->
            val exam = realm.where(RealmStepExam::class.java).equalTo("id", id).findFirst()
            exam?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getQuestions(exam: RealmStepExam?): List<RealmExamQuestion>? {
        return withRealm { realm ->
            val questions = realm.where(RealmExamQuestion::class.java)
                .equalTo("examId", exam?.id)
                .sort("header", Sort.ASCENDING)
                .findAll()
            questions?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun saveCourseProgress(exam: RealmStepExam?, stepNumber: Int, sub: RealmSubmission?) {
        withContext(Dispatchers.IO) {
            databaseService.realmInstance.executeTransaction { realm ->
                val progress = realm.where(RealmCourseProgress::class.java)
                    .equalTo("courseId", exam?.courseId)
                    .equalTo("stepNum", stepNumber).findFirst()
                progress?.passed = sub?.status == "graded"
            }
        }
    }

    override suspend fun insertIntoSubmitPhotos(
        submitId: String?,
        exam: RealmStepExam?,
        userId: String?,
        date: String,
        uniqueId: String,
        photoPath: String?
    ) {
        withContext(Dispatchers.IO) {
            databaseService.realmInstance.executeTransaction { realm ->
                val submit = realm.createObject(RealmSubmitPhotos::class.java, UUID.randomUUID().toString())
                submit.submissionId = submitId
                submit.examId = exam?.id
                submit.courseId = exam?.courseId
                submit.memberId = userId
                submit.date = date
                submit.uniqueId = uniqueId
                submit.photoLocation = photoPath
                submit.uploaded = false
            }
        }
    }

    override suspend fun updateSubmissionStatus(subId: String, status: String) {
        withContext(Dispatchers.IO) {
            databaseService.realmInstance.executeTransaction { realm ->
                val submission = realm.where(RealmSubmission::class.java).equalTo("id", subId).findFirst()
                submission?.status = status
            }
        }
    }

    override suspend fun clearAllSubmissions(userId: String?, parentId: String?) {
        withContext(Dispatchers.IO) {
            databaseService.realmInstance.executeTransaction { realm ->
                val allSubmissions = realm.where(RealmSubmission::class.java)
                    .equalTo("userId", userId)
                    .equalTo("parentId", parentId)
                    .findAll()
                allSubmissions.forEach { submission ->
                    submission.answers?.deleteAllFromRealm()
                    submission.deleteFromRealm()
                }
            }
        }
    }

    override suspend fun saveAnswer(
        submission: RealmSubmission?,
        question: RealmExamQuestion,
        ans: String,
        listAns: Map<String, String>?,
        otherText: String?,
        otherVisible: Boolean,
        type: String,
        index: Int,
        total: Int
    ): Boolean {
        return withContext(Dispatchers.IO) {
            databaseService.realmInstance.executeTransaction { realm ->
                val realmSubmission = submission?.id?.let {
                    realm.where(RealmSubmission::class.java).equalTo("id", it).findFirst()
                } ?: realm.where(RealmSubmission::class.java)
                    .equalTo("status", "pending")
                    .findAll().lastOrNull()

                val realmQuestion = realm.where(RealmExamQuestion::class.java).equalTo("id", question.id).findFirst()

                if (realmSubmission != null && realmQuestion != null) {
                    val answer = createOrRetrieveAnswer(realm, realmSubmission, realmQuestion)
                    populateAnswer(answer, realmQuestion, ans, listAns, otherText, otherVisible)

                    if (type == "exam") {
                        val isCorrect = org.ole.planet.myplanet.ui.exam.ExamAnswerUtils.checkCorrectAnswer(ans, listAns, realmQuestion)
                        answer.isPassed = isCorrect
                        answer.grade = 1
                        if (!isCorrect) {
                            answer.mistakes = answer.mistakes + 1
                        }
                    }
                    updateSubmissionStatus(realm, realmSubmission, index, total, type)
                }
            }
            if (type == "exam") {
                org.ole.planet.myplanet.ui.exam.ExamAnswerUtils.checkCorrectAnswer(ans, listAns, question)
            } else {
                true
            }
        }
    }

    private fun createOrRetrieveAnswer(
        realm: Realm,
        submission: RealmSubmission?,
        question: RealmExamQuestion,
    ): org.ole.planet.myplanet.model.RealmAnswer {
        val existing = submission?.answers?.find { it.questionId == question.id }

        val ansObj = if (existing != null) {
            existing
        } else {
            val newAnswerId = UUID.randomUUID().toString()
            val newAnswer = realm.createObject(org.ole.planet.myplanet.model.RealmAnswer::class.java, newAnswerId)
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
        submission?.lastUpdateTime = java.util.Date().time
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
        answer: org.ole.planet.myplanet.model.RealmAnswer, question: RealmExamQuestion, ans: String, listAns: Map<String, String>?,
        otherText: String?, otherVisible: Boolean,
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
        answer: org.ole.planet.myplanet.model.RealmAnswer, question: RealmExamQuestion, ans: String, otherText: String?,
        otherVisible: Boolean,
    ) {
        if (otherVisible && !otherText.isNullOrEmpty()) {
            answer.value = otherText
            answer.valueChoices = io.realm.RealmList<String>().apply {
                add("""{"id":"other","text":"$otherText"}""")
            }
        } else {
            val choiceText = org.ole.planet.myplanet.ui.exam.ExamAnswerUtils.getChoiceTextById(question, ans)
            answer.value = choiceText
            answer.valueChoices = io.realm.RealmList<String>().apply {
                if (ans.isNotEmpty()) {
                    add("""{"id":"$ans","text":"$choiceText"}""")
                }
            }
        }
    }

    private fun populateMultipleSelectAnswer(
        answer: org.ole.planet.myplanet.model.RealmAnswer, listAns: Map<String, String>?, otherText: String?, otherVisible: Boolean
    ) {
        answer.value = ""
        answer.valueChoices = io.realm.RealmList<String>().apply {
            listAns?.forEach { (text, id) ->
                if (id == "other" && otherVisible && !otherText.isNullOrEmpty()) {
                    add("""{"id":"other","text":"$otherText"}""")
                } else {
                    add("""{"id":"$id","text":"$text"}""")
                }
            }
        }
    }

    private fun populateTextAnswer(answer: org.ole.planet.myplanet.model.RealmAnswer, ans: String) {
        answer.value = ans
        answer.valueChoices = null
    }

    override suspend fun createSubmission(
        submission: RealmSubmission?,
        user: org.ole.planet.myplanet.model.RealmUserModel?,
        exam: RealmStepExam?,
        id: String?,
        isTeam: Boolean,
        teamId: String?
    ) {
        withContext(Dispatchers.IO) {
            databaseService.realmInstance.executeTransaction { realm ->
                val sub = realm.createObject(RealmSubmission::class.java, UUID.randomUUID().toString())
                submission?.id = sub.id
                submission?.parentId = when {
                    !android.text.TextUtils.isEmpty(exam?.id) -> if (!android.text.TextUtils.isEmpty(exam?.courseId)) {
                        "${exam?.id}@${exam?.courseId}"
                    } else {
                        exam?.id
                    }
                    !android.text.TextUtils.isEmpty(id) -> if (!android.text.TextUtils.isEmpty(exam?.courseId)) {
                        "$id@${exam?.courseId}"
                    } else {
                        id
                    }
                    else -> submission?.parentId
                }
                try {
                    val parentJsonString = org.json.JSONObject().apply {
                        put("_id", exam?.id ?: id)
                        put("name", exam?.name ?: "")
                        put("courseId", exam?.courseId ?: "")
                        put("sourcePlanet", exam?.sourcePlanet ?: "")
                        put("teamShareAllowed", exam?.isTeamShareAllowed ?: false)
                        put("noOfQuestions", exam?.noOfQuestions ?: 0)
                        put("isFromNation", exam?.isFromNation ?: false)
                    }.toString()
                    submission?.parent = parentJsonString
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                submission?.userId = user?.id
                submission?.status = "pending"
                submission?.type = submission?.type
                submission?.startTime = java.util.Date().time
                submission?.lastUpdateTime = java.util.Date().time
                if (submission?.answers == null) {
                    submission?.answers = io.realm.RealmList()
                }
            }
        }
    }

    override suspend fun getSubmission(userId: String?, parentId: String?, type: String?): RealmSubmission? {
        return withRealm { realm ->
            var q: io.realm.RealmQuery<RealmSubmission> = realm.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .equalTo("parentId", parentId)
                .sort("startTime", io.realm.Sort.DESCENDING)
            if (type == "exam") {
                q = q.equalTo("status", "pending")
            }
            val sub = q.findFirst()
            sub?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun isCourseCertified(courseId: String?): Boolean {
        return withRealm { realm ->
            realm.where(org.ole.planet.myplanet.model.RealmCertification::class.java)
                .equalTo("courseId", courseId)
                .findFirst() != null
        }
    }
}
