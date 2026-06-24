package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import java.util.Locale
import org.ole.planet.myplanet.utils.JsonUtils

open class RealmExamQuestion : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var header: String? = null
    var body: String? = null
    var type: String? = null
    @Index
    var examId: String? = null
    private var correctChoice: RealmList<String>? = null
    var marks: String? = null
    var choices: String? = null
    var hasOtherOption: Boolean = false
    var scaleMax: Int = 9
    private fun setCorrectChoiceArray(array: JsonArray, question: RealmExamQuestion?) {
        for (i in 0 until array.size()) {
            question?.correctChoice?.add(JsonUtils.getString(array, i).lowercase(Locale.getDefault()))
        }
    }

    fun getCorrectChoice(): RealmList<String>? {
        return correctChoice
    }

    val correctChoiceArray: JsonArray
        get() {
            val array = JsonArray()
            for (s in correctChoice ?: emptyList()){
                array.add(s)
            }
            return array
        }

    companion object {
        @JvmStatic
        fun mapToDetached(questions: JsonArray, examId: String?): List<RealmExamQuestion> {
            if (questions.size() == 0) return emptyList()

            val detachedList = mutableListOf<RealmExamQuestion>()
            for (i in 0 until questions.size()) {
                val question = questions[i].asJsonObject
                val questionId = if (question.has("id")) {
                    JsonUtils.getString("id", question)
                } else {
                    "$examId-${i}"
                }

                val myQuestion = RealmExamQuestion()
                myQuestion.id = questionId
                myQuestion.examId = examId
                myQuestion.body = JsonUtils.getString("body", question)
                myQuestion.type = JsonUtils.getString("type", question)
                myQuestion.header = JsonUtils.getString("title", question)
                myQuestion.marks = JsonUtils.getString("marks", question)
                myQuestion.choices = if (question.has("choices")) {
                    JsonUtils.gson.toJson(JsonUtils.getJsonArray("choices", question))
                } else {
                    "[]"
                }

                myQuestion.hasOtherOption = JsonUtils.getBoolean("hasOtherOption", question)
                myQuestion.scaleMax = JsonUtils.getInt("scaleMax", question).let { if (it <= 0) 9 else it }
                val isMultipleChoice = myQuestion.type?.startsWith("select") == true && question.has("choices")
                if (isMultipleChoice) {
                    insertCorrectChoice(question["choices"].asJsonArray, question, myQuestion)
                }
                detachedList.add(myQuestion)
            }
            return detachedList
        }

@JvmStatic
        fun insertExamQuestions(questions: JsonArray, examId: String?, mRealm: Realm) {
            val detachedQuestions = mapToDetached(questions, examId)
            if (detachedQuestions.isNotEmpty()) {
                mRealm.insertOrUpdate(detachedQuestions)
            }
        }

        private fun insertCorrectChoice(array: JsonArray, question: JsonObject, myQuestion: RealmExamQuestion?) {
            for (a in 0 until array.size()) {
                val res = array[a].asJsonObject
                if (question["correctChoice"].isJsonArray) {
                    myQuestion?.correctChoice = RealmList()
                    myQuestion?.setCorrectChoiceArray(JsonUtils.getJsonArray("correctChoice", question), myQuestion)
                } else if (JsonUtils.getString("correctChoice", question) == JsonUtils.getString("id", res)) {
                    myQuestion?.correctChoice = RealmList()
                    myQuestion?.correctChoice?.add(JsonUtils.getString("res", res))
                }
            }
        }

        @JvmStatic
        fun serializeQuestions(question: List<RealmExamQuestion>): JsonArray {
            val array = JsonArray()
            for (que in question) {
                val `object` = JsonObject()
                `object`.addProperty("header", que.header)
                `object`.addProperty("body", que.body)
                `object`.addProperty("type", que.type)
                `object`.addProperty("marks", que.marks)
                `object`.add("choices", JsonUtils.getStringAsJsonArray(que.choices))
                `object`.add("correctChoice", que.correctChoiceArray)
                `object`.addProperty("hasOtherOption", que.hasOtherOption)
                array.add(`object`)
            }
            return array
        }
    }
}
