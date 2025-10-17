package org.ole.planet.myplanet.ui.exam

import android.util.Log
import io.realm.Realm
import io.realm.RealmList
import java.util.Date
import java.util.UUID
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmSubmission

object ExamSubmissionUtils {
    private const val TAG = "ExamSubmissionUtils"
    fun saveAnswer(
        realm: Realm,
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
        Log.d(TAG, "saveAnswer: Called for questionId=${question.id}, questionType=${question.type}")
        Log.d(TAG, "saveAnswer: ans='$ans', ansIsEmpty=${ans.isEmpty()}")
        Log.d(TAG, "saveAnswer: listAns=$listAns, listAnsSize=${listAns?.size}")
        Log.d(TAG, "saveAnswer: otherText='$otherText', otherVisible=$otherVisible")
        Log.d(TAG, "saveAnswer: type=$type, index=$index, total=$total")

        val submissionId = try {
            submission?.id
        } catch (e: IllegalStateException) {
            null
        }

        val questionId = question.id
        realm.executeTransactionAsync { r ->
            val realmSubmission = if (submissionId != null) {
                r.where(RealmSubmission::class.java).equalTo("id", submissionId).findFirst()
            } else {
                r.where(RealmSubmission::class.java)
                    .equalTo("status", "pending")
                    .findAll().lastOrNull()
            }
            
            val realmQuestion = r.where(RealmExamQuestion::class.java).equalTo("id", questionId).findFirst()
            
            if (realmSubmission != null && realmQuestion != null) {
                val answer = createOrRetrieveAnswer(r, realmSubmission, realmQuestion)
                populateAnswer(answer, realmQuestion, ans, listAns, otherText, otherVisible)
                if (type == "exam") {
                    val isCorrect = ExamAnswerUtils.checkCorrectAnswer(ans, listAns, realmQuestion)
                    answer.isPassed = isCorrect
                    answer.grade = 1
                    if (!isCorrect) {
                        answer.mistakes = answer.mistakes + 1
                    }
                }
                updateSubmissionStatus(r, realmSubmission, index, total, type)
            }
        }

        return if (type == "exam") {
            ExamAnswerUtils.checkCorrectAnswer(ans, listAns, question)
        } else {
            true
        }
    }

    private fun createOrRetrieveAnswer(
        realm: Realm,
        submission: RealmSubmission?,
        question: RealmExamQuestion,
    ): RealmAnswer {
        val existing = submission?.answers?.find { it.questionId == question.id }
        val ansObj = existing ?: realm.createObject(RealmAnswer::class.java, UUID.randomUUID().toString())
        if (existing == null) {
            submission?.answers?.add(ansObj)
        }
        ansObj.questionId = question.id
        ansObj.submissionId = submission?.id
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
        Log.d(TAG, "populateAnswer: questionType=${question.type}, questionId=${question.id}")
        when {
            question.type.equals("select", ignoreCase = true) -> {
                Log.d(TAG, "populateAnswer: Using populateSelectAnswer")
                populateSelectAnswer(answer, question, ans, otherText, otherVisible)
            }
            question.type.equals("selectMultiple", ignoreCase = true) -> {
                Log.d(TAG, "populateAnswer: Using populateMultipleSelectAnswer")
                populateMultipleSelectAnswer(answer, listAns, otherText, otherVisible)
            }
            else -> {
                Log.d(TAG, "populateAnswer: Using populateTextAnswer (questionType=${question.type})")
                // For text/textarea questions, use otherText if available (when input field is visible)
                val textValue = if (otherVisible && !otherText.isNullOrEmpty()) {
                    Log.d(TAG, "populateAnswer: Using otherText='$otherText' instead of ans='$ans'")
                    otherText
                } else {
                    Log.d(TAG, "populateAnswer: Using ans='$ans'")
                    ans
                }
                populateTextAnswer(answer, textValue)
            }
        }
        Log.d(TAG, "populateAnswer: RESULT - answer.value='${answer.value}', valueChoicesSize=${answer.valueChoices?.size}")
    }

    private fun populateSelectAnswer(
        answer: RealmAnswer,
        question: RealmExamQuestion,
        ans: String,
        otherText: String?,
        otherVisible: Boolean,
    ) {
        Log.d(TAG, "populateSelectAnswer: ans='$ans', otherVisible=$otherVisible, otherText='$otherText'")
        if (otherVisible && !otherText.isNullOrEmpty()) {
            Log.d(TAG, "populateSelectAnswer: Using 'other' option, setting value='$otherText'")
            answer.value = otherText
            answer.valueChoices = RealmList<String>().apply {
                add("""{"id":"other","text":"$otherText"}""")
            }
        } else {
            val choiceText = ExamAnswerUtils.getChoiceTextById(question, ans)
            Log.d(TAG, "populateSelectAnswer: ans='$ans', choiceText='$choiceText'")
            Log.d(TAG, "populateSelectAnswer: Setting answer.value='$choiceText'")
            answer.value = choiceText
            answer.valueChoices = RealmList<String>().apply {
                if (ans.isNotEmpty()) {
                    add("""{"id":"$ans","text":"$choiceText"}""")
                    Log.d(TAG, "populateSelectAnswer: Added to valueChoices: id=$ans, text=$choiceText")
                } else {
                    Log.w(TAG, "populateSelectAnswer: ans is empty, NOT adding to valueChoices")
                }
            }
        }
    }

    private fun populateMultipleSelectAnswer(
        answer: RealmAnswer,
        listAns: Map<String, String>?,
        otherText: String?,
        otherVisible: Boolean,
    ) {
        Log.d(TAG, "populateMultipleSelectAnswer: listAns=$listAns, otherVisible=$otherVisible, otherText='$otherText'")
        Log.w(TAG, "populateMultipleSelectAnswer: Setting answer.value to EMPTY STRING (this is intentional for multiple choice)")
        answer.value = ""
        answer.valueChoices = RealmList<String>().apply {
            listAns?.toMap()?.forEach { (text, id) ->
                if (id == "other" && otherVisible && !otherText.isNullOrEmpty()) {
                    add("""{"id":"other","text":"$otherText"}""")
                    Log.d(TAG, "populateMultipleSelectAnswer: Added 'other' choice: text='$otherText'")
                } else {
                    add("""{"id":"$id","text":"$text"}""")
                    Log.d(TAG, "populateMultipleSelectAnswer: Added choice: id='$id', text='$text'")
                }
            }
        }
        Log.d(TAG, "populateMultipleSelectAnswer: Total valueChoices added: ${answer.valueChoices?.size}")
    }

    private fun populateTextAnswer(answer: RealmAnswer, ans: String) {
        Log.d(TAG, "populateTextAnswer: ans='$ans', ansIsEmpty=${ans.isEmpty()}, ansLength=${ans.length}")
        if (ans.isEmpty()) {
            Log.w(TAG, "populateTextAnswer: WARNING - Setting answer.value to EMPTY STRING")
        }
        answer.value = ans
        answer.valueChoices = null
    }
}
