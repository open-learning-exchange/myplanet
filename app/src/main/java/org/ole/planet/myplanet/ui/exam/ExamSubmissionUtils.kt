package org.ole.planet.myplanet.ui.exam

import io.realm.Realm
import io.realm.RealmList
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmSubmission
import java.util.Date
import java.util.UUID

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
        var isCorrect = true
        realm.executeTransaction { r ->
            val answer = createOrRetrieveAnswer(r, submission, question)
            populateAnswer(answer, question, ans, listAns, otherText, otherVisible)
            if (type == "exam") {
                isCorrect = ExamAnswerUtils.checkCorrectAnswer(ans, listAns, question)
                answer.isPassed = isCorrect
                answer.grade = 1
                if (!isCorrect) {
                    answer.mistakes = (answer.mistakes ?: 0) + 1
                }
            }
            updateSubmissionStatus(submission, index, total, type)
        }
        return if (type == "exam") isCorrect else true
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
        submission: RealmSubmission?,
        index: Int,
        total: Int,
        type: String,
    ) {
        submission?.lastUpdateTime = Date().time
        submission?.status = if (index == total - 1) {
            if (type == "survey") "complete" else "requires grading"
        } else {
            "pending"
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
        if (ans == "other" && otherVisible && !otherText.isNullOrEmpty()) {
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
