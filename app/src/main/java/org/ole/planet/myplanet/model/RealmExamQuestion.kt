package org.ole.planet.myplanet.model

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.annotations.PrimaryKey
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.kotlin.Realm
import org.ole.planet.myplanet.utilities.JsonUtils
import java.util.Locale

class RealmExamQuestion : RealmObject {
    @PrimaryKey
    var id: String? = null
    var header: String? = null
    var body: String? = null
    var type: String? = null
    var examId: String? = null
    var correctChoice: RealmList<String> = realmListOf()
    var marks: String? = null
    var choices: String? = null

    private fun setCorrectChoiceArray(array: JsonArray) {
        correctChoice.clear()
        for (i in 0 until array.size()) {
            correctChoice.add(array[i].asString.lowercase(Locale.getDefault()))
        }
    }

    val correctChoiceArray: JsonArray
        get() {
            val array = JsonArray()
            for (s in correctChoice) {
                array.add(s)
            }
            return array
        }

    companion object {
        suspend fun insertExamQuestions(questions: JsonArray, examId: String?, realm: Realm) {
            realm.write {
                for (i in 0 until questions.size()) {
                    val question = questions[i].asJsonObject
                    val questionId = question.toString().hashCode().toString() // Generate a unique ID
                    var myQuestion = query<RealmExamQuestion>(RealmExamQuestion::class, "id == $0", questionId).first().find()
                    if (myQuestion == null) {
                        myQuestion = RealmExamQuestion().apply { id = questionId }
                        copyToRealm(myQuestion)
                    }
                    myQuestion.examId = examId
                    myQuestion.body = question["body"]?.asString
                    myQuestion.type = question["type"]?.asString
                    myQuestion.header = question["title"]?.asString
                    myQuestion.marks = question["marks"]?.asString
                    myQuestion.choices = question["choices"]?.asJsonArray?.toString()

                    val isMultipleChoice = question.has("correctChoice") && (myQuestion.type?.startsWith("select") == true)
                    if (isMultipleChoice) {
                        val choicesArray = question["correctChoice"]?.asJsonArray
                        if (choicesArray != null) {
                            insertCorrectChoice(choicesArray, question, myQuestion)
                        } else {
                            val correctChoice = question["correctChoice"]?.asString
                            if (correctChoice != null) {
                                myQuestion.correctChoice.clear()
                                myQuestion.correctChoice.add(correctChoice)
                            }
                        }
                    }
                }
            }
        }

        private fun insertCorrectChoice(array: JsonArray, question: JsonObject, myQuestion: RealmExamQuestion?) {
            myQuestion?.correctChoice = realmListOf()

            for (a in 0 until array.size()) {
                val res = array[a].asJsonObject
                if (question["correctChoice"].isJsonArray) {
                    myQuestion?.setCorrectChoiceArray(JsonUtils.getJsonArray("correctChoice", question))
                } else {
                    val correctChoice = JsonUtils.getString("correctChoice", question)
                    if (correctChoice == JsonUtils.getString("id", res)) {
                        myQuestion?.correctChoice?.add(JsonUtils.getString("res", res))
                    }
                }
            }
        }

        fun serializeQuestions(questions: List<RealmExamQuestion>): JsonArray {
            val array = JsonArray()
            for (question in questions) {
                val jsonObject = JsonObject()
                jsonObject.addProperty("header", question.header)
                jsonObject.addProperty("body", question.body)
                jsonObject.addProperty("type", question.type)
                jsonObject.addProperty("marks", question.marks)
                jsonObject.add("choices", JsonArray().apply {
                    question.choices?.let { add(it) }
                })
                jsonObject.add("correctChoice", question.correctChoiceArray)
                array.add(jsonObject)
            }
            return array
        }
    }
}
