package org.ole.planet.myplanet.ui.exam

import io.realm.Realm
import io.realm.RealmList
import java.util.Date
import java.util.UUID
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.utilities.ExamAnswerUtils

object ExamSubmissionUtils {
    fun saveAnswer(
        realm: Realm, submission: RealmSubmission?, question: RealmExamQuestion,
        ans: String, listAns: Map<String, String>?, otherText: String?, otherVisible: Boolean,
        type: String, index: Int, total: Int
    ): Boolean {
        val submissionId = try {
            submission?.id
        } catch (e: IllegalStateException) {
            null
        }

        val questionId = question.id
        realm.executeTransactionAsync({ r ->
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
        }, {
            // Success
        }, { _ ->
            // Error
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
        answer: RealmAnswer, question: RealmExamQuestion, ans: String, listAns: Map<String, String>?,
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
        answer: RealmAnswer, question: RealmExamQuestion, ans: String, otherText: String?,
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
        answer: RealmAnswer, listAns: Map<String, String>?, otherText: String?, otherVisible: Boolean
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
