package org.ole.planet.myplanet.model

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.JsonParserUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import java.util.Locale

open class RealmExamQuestion : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var header: String? = null
    var body: String? = null
    var type: String? = null
    var examId: String? = null
    private var correctChoice: RealmList<String>? = null
    var marks: String? = null
    var choices: String? = null
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
        fun insertExamQuestions(questions: JsonArray, examId: String?, mRealm: Realm) {
            if (questions.size() == 0) return

//            mRealm.executeTransaction { realm ->
                for (i in 0 until questions.size()) {
                    val question = questions[i].asJsonObject
                    // Better ID generation - use a unique field or random UUID if none exists
                    val questionId = if (question.has("id")) {
                        JsonUtils.getString("id", question)
                    } else {
                        // Fallback: generate ID from examId + index
                        "$examId-${i}"
                        // Or use UUID.randomUUID().toString() for completely random IDs
                    }

                    var myQuestion = mRealm.where(RealmExamQuestion::class.java)
                        .equalTo("id", questionId)
                        .findFirst()

                    if (myQuestion == null) {
                        myQuestion = mRealm.createObject(RealmExamQuestion::class.java, questionId)
                    }

                    myQuestion.apply {
                        this.examId = examId
                        body = JsonUtils.getString("body", question)
                        type = JsonUtils.getString("type", question)
                        header = JsonUtils.getString("title", question)
                        marks = JsonUtils.getString("marks", question) // default to 1 mark if not specified

                        // Handle choices
                        if (question.has("choices")) {
                            choices = Gson().toJson(JsonUtils.getJsonArray("choices", question))
                        } else {
                            choices = "[]"
                        }

                        // Handle correct choice
                        val isMultipleChoice = type?.startsWith("select") == true && question.has("choices")
                        if (isMultipleChoice) {
                            insertCorrectChoice(question["choices"].asJsonArray, question, this)
                        }
                    }
                }
//            }
        }
//        fun insertExamQuestions(questions: JsonArray, examId: String?, mRealm: Realm) {
//            for (i in 0 until questions.size()) {
//                val question = questions[i].asJsonObject
//                val questionId = Base64.encodeToString(question.toString().toByteArray(), Base64.NO_WRAP)
//                var myQuestion = mRealm.where(RealmExamQuestion::class.java).equalTo("id", questionId).findFirst()
//                if (myQuestion == null) {
//                    myQuestion = mRealm.createObject(RealmExamQuestion::class.java, questionId)
//                }
//                myQuestion?.examId = examId
//                myQuestion?.body = JsonUtils.getString("body", question)
//                myQuestion?.type = JsonUtils.getString("type", question)
//                myQuestion?.header = JsonUtils.getString("title", question)
//                myQuestion?.marks = JsonUtils.getString("marks", question)
//                myQuestion?.choices = Gson().toJson(JsonUtils.getJsonArray("choices", question))
//                val isMultipleChoice = question.has("correctChoice") && JsonUtils.getString("type", question).startsWith("select")
//                if (isMultipleChoice) {
//                    insertCorrectChoice(question["choices"].asJsonArray, question, myQuestion)
//                }
//            }
//        }

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
        fun serializeQuestions(question: RealmResults<RealmExamQuestion>): JsonArray {
            val array = JsonArray()
            for (que in question) {
                val `object` = JsonObject()
                `object`.addProperty("header", que.header)
                `object`.addProperty("body", que.body)
                `object`.addProperty("type", que.type)
                `object`.addProperty("marks", que.marks)
                `object`.add("choices", JsonParserUtils.getStringAsJsonArray(que.choices))
                `object`.add("correctChoice", que.correctChoiceArray)
                array.add(`object`)
            }
            return array
        }
    }
}