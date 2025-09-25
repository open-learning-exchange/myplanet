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
        Log.d("RealmAnswerSave", "=== Saving Answer to Realm ===")
        Log.d("RealmAnswerSave", "Question ID: ${question.id}")
        Log.d("RealmAnswerSave", "Question: ${question.header ?: question.body}")
        Log.d("RealmAnswerSave", "Question Type: ${question.type}")
        Log.d("RealmAnswerSave", "Answer (ans): '$ans'")
        Log.d("RealmAnswerSave", "List Answers: $listAns")
        Log.d("RealmAnswerSave", "Other Text: '$otherText'")
        Log.d("RealmAnswerSave", "Other Visible: $otherVisible")
        Log.d("RealmAnswerSave", "Submission ID: ${submission?.id}")
        Log.d("RealmAnswerSave", "Type: $type")
        Log.d("RealmAnswerSave", "Index: $index/$total")
        val submissionId = try {
            submission?.id
        } catch (e: IllegalStateException) {
            null
        }

        val questionId = question.id
        realm.executeTransactionAsync({ r ->
            Log.d("RealmAnswerSave", "--- Starting Realm Transaction ---")
            val realmSubmission = if (submissionId != null) {
                Log.d("RealmAnswerSave", "Finding submission by ID: $submissionId")
                r.where(RealmSubmission::class.java).equalTo("id", submissionId).findFirst()
            } else {
                Log.d("RealmAnswerSave", "Finding last pending submission")
                r.where(RealmSubmission::class.java)
                    .equalTo("status", "pending")
                    .findAll().lastOrNull()
            }

            val realmQuestion = r.where(RealmExamQuestion::class.java).equalTo("id", questionId).findFirst()

            Log.d("RealmAnswerSave", "Found submission: ${realmSubmission != null}")
            Log.d("RealmAnswerSave", "Found question: ${realmQuestion != null}")
            if (realmSubmission != null && realmQuestion != null) {
                Log.d("RealmAnswerSave", "Creating/retrieving answer...")
                val answer = createOrRetrieveAnswer(r, realmSubmission, realmQuestion)
                Log.d("RealmAnswerSave", "Answer ID: ${answer.id}")
                Log.d("RealmAnswerSave", "Populating answer...")
                populateAnswer(answer, realmQuestion, ans, listAns, otherText, otherVisible)

                Log.d("RealmAnswerSave", "Answer after population:")
                Log.d("RealmAnswerSave", "  Value: '${answer.value}'")
                Log.d("RealmAnswerSave", "  ValueChoices: ${answer.valueChoices?.joinToString()}")
                if (type == "exam") {
                    val isCorrect = ExamAnswerUtils.checkCorrectAnswer(ans, listAns, realmQuestion)
                    answer.isPassed = isCorrect
                    answer.grade = 1
                    if (!isCorrect) {
                        answer.mistakes = answer.mistakes + 1
                    }
                    Log.d("RealmAnswerSave", "Exam grading - Correct: $isCorrect, Mistakes: ${answer.mistakes}")
                }

                Log.d("RealmAnswerSave", "Updating submission status...")
                updateSubmissionStatus(r, realmSubmission, index, total, type)
                Log.d("RealmAnswerSave", "Submission status: ${realmSubmission.status}")
                Log.d("RealmAnswerSave", "Total answers in submission: ${realmSubmission.answers?.size ?: 0}")
            } else {
                Log.w("RealmAnswerSave", "Cannot save answer - missing submission or question")
            }
            Log.d("RealmAnswerSave", "--- Transaction Complete ---")
        }, {
            Log.d("RealmAnswerSave", "Realm transaction completed successfully")
        }, { error ->
            Log.e("RealmAnswerSave", "Realm transaction failed", error)
        })

        val result = if (type == "exam") {
            ExamAnswerUtils.checkCorrectAnswer(ans, listAns, question)
        } else {
            true
        }
        return result
    }

    private fun createOrRetrieveAnswer(
        realm: Realm,
        submission: RealmSubmission?,
        question: RealmExamQuestion,
    ): RealmAnswer {
        Log.d("RealmAnswerSave", "  Looking for existing answer with questionId: ${question.id}")
        val existing = submission?.answers?.find { it.questionId == question.id }
        Log.d("RealmAnswerSave", "  Found existing answer: ${existing != null}")

        val ansObj = if (existing != null) {
            Log.d("RealmAnswerSave", "  Using existing answer with ID: ${existing.id}")
            existing
        } else {
            val newAnswerId = UUID.randomUUID().toString()
            Log.d("RealmAnswerSave", "  Creating new answer with ID: $newAnswerId")
            val newAnswer = realm.createObject(RealmAnswer::class.java, newAnswerId)
            submission?.answers?.add(newAnswer)
            Log.d("RealmAnswerSave", "  Added answer to submission. Total answers: ${submission?.answers?.size}")
            newAnswer
        }

        ansObj.questionId = question.id
        ansObj.submissionId = submission?.id
        ansObj.examId = question.examId
        Log.d("RealmAnswerSave", "  Answer configured - QuestionId: ${ansObj.questionId}, SubmissionId: ${ansObj.submissionId}, ExamId: ${ansObj.examId}")
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
            question.type.equals("select", ignoreCase = true) ->
                populateSelectAnswer(answer, question, ans, otherText, otherVisible)
            question.type.equals("selectMultiple", ignoreCase = true) ->
                populateMultipleSelectAnswer(answer, listAns, otherText, otherVisible)
            else ->
                populateTextAnswer(answer, ans)
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
        otherVisible: Boolean,
    ) {
        answer.value = ""
        answer.valueChoices = RealmList<String>().apply {
            listAns?.toMap()?.forEach { (text, id) ->
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
