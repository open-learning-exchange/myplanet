package org.ole.planet.myplanet.utilities

import android.util.Log
import io.realm.Realm
import io.realm.RealmList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmSubmission
import java.util.Date
import java.util.UUID

object ExamSubmissionUtils {
    suspend fun saveAnswer(
        submissionId: String?, questionId: String,
        ans: String, listAns: Map<String, String>?, otherText: String?, otherVisible: Boolean,
        type: String, index: Int, total: Int, isExplicitSubmission: Boolean = false
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val realm = Realm.getDefaultInstance()
            try {
                var isCorrect = true
                realm.beginTransaction()

                val realmSubmission = if (submissionId != null) {
                    realm.where(RealmSubmission::class.java).equalTo("id", submissionId).findFirst()
                } else {
                    realm.where(RealmSubmission::class.java)
                        .equalTo("status", "pending")
                        .findAll().lastOrNull()
                }

                val realmQuestion = realm.where(RealmExamQuestion::class.java).equalTo("id", questionId).findFirst()

                if (realmSubmission != null && realmQuestion != null) {
                    val answer = createOrRetrieveAnswer(realm, realmSubmission, realmQuestion)
                    populateAnswer(answer, realmQuestion, ans, listAns, otherText, otherVisible)

                    if (type == "exam") {
                        isCorrect = ExamAnswerUtils.checkCorrectAnswer(ans, listAns, realmQuestion)
                        answer.isPassed = isCorrect
                        answer.grade = 1
                        if (!isCorrect) {
                            answer.mistakes += 1
                        }
                    }

                    updateSubmissionStatus(realmSubmission, index, total, type, isExplicitSubmission)
                }
                realm.commitTransaction()
                Result.success(isCorrect)
            } catch (e: Exception) {
                if (realm.isInTransaction) {
                    realm.cancelTransaction()
                }
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }
                Log.e("ExamSubmission", "Error saving answer: ${e.message}", e)
                Result.failure(e)
            } finally {
                realm.close()
            }
        }
    }

    private fun createOrRetrieveAnswer(
        realm: Realm, submission: RealmSubmission?, question: RealmExamQuestion,
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
        submission: RealmSubmission?, index: Int, total: Int, type: String,
        isExplicitSubmission: Boolean = false
    ) {
        submission?.lastUpdateTime = Date().time
        val isFinal = index == total - 1

        submission?.status = when {
            isFinal && isExplicitSubmission && type == "survey" -> "complete"
            isFinal && isExplicitSubmission -> "requires grading"
            else -> "pending"
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
