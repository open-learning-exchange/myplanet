package org.ole.planet.myplanet.model
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Locale
import org.ole.planet.myplanet.utils.JsonUtils

@Entity(tableName = "exam_questions", indices = [Index("examId")])
open class ExamQuestion(
    @PrimaryKey @JvmField var id: String = "",
    var header: String? = null,
    @ColumnInfo(name = "question") var body: String? = null,
    var type: String? = null,
    var examId: String? = null,
    @Ignore private var correctChoice: MutableList<String>? = null,
    var marks: String? = null,
    var choices: String? = null,
    var hasOtherOption: Boolean = false,
    var scaleMax: Int = 9
) {
    private fun setCorrectChoiceArray(array: JsonArray, question: ExamQuestion?) {
        for (i in 0 until array.size()) {
            question?.correctChoice?.add(JsonUtils.getString(array, i).lowercase(Locale.getDefault()))
        }
    }

    fun getCorrectChoice(): List<String>? {
        return correctChoice
    }

    fun setCorrectChoices(choices: List<String>?) {
        correctChoice = mutableListOf<String>().apply {
            choices.orEmpty().forEach { add(it) }
        }
    }

    @get:Ignore
    val correctChoiceArray: JsonArray
        get() {
            val array = JsonArray()
            for (s in correctChoice ?: emptyList()){
                array.add(s)
            }
            return array
        }

    companion object {
        fun insertExamQuestions(questions: JsonArray, examId: String?): List<ExamQuestion> {
            if (questions.size() == 0) return emptyList()

            val questionsToInsert = mutableListOf<ExamQuestion>()

            for (i in 0 until questions.size()) {
                val question = questions[i].asJsonObject
                val questionId = if (question.has("id")) {
                    JsonUtils.getString("id", question)
                } else {
                    "$examId-${i}"
                }

                val myQuestion = ExamQuestion().apply {
                    this.id = questionId
                    this.examId = examId
                    body = JsonUtils.getString("body", question)
                    type = JsonUtils.getString("type", question)
                    header = JsonUtils.getString("title", question)
                    marks = JsonUtils.getString("marks", question)
                    choices = if (question.has("choices")) {
                        JsonUtils.gson.toJson(JsonUtils.getJsonArray("choices", question))
                    } else {
                        "[]"
                    }

                    hasOtherOption = JsonUtils.getBoolean("hasOtherOption", question)
                    scaleMax = JsonUtils.getInt("scaleMax", question).let { if (it <= 0) 9 else it }
                    val isMultipleChoice = type?.startsWith("select") == true && question.has("choices")
                    if (isMultipleChoice) {
                        insertCorrectChoice(question["choices"].asJsonArray, question, this)
                    }
                }
                questionsToInsert.add(myQuestion)
            }
            return questionsToInsert
        }

        private fun insertCorrectChoice(array: JsonArray, question: JsonObject, myQuestion: ExamQuestion?) {
            for (a in 0 until array.size()) {
                val res = array[a].asJsonObject
                if (question["correctChoice"].isJsonArray) {
                    myQuestion?.correctChoice = mutableListOf()
                    myQuestion?.setCorrectChoiceArray(JsonUtils.getJsonArray("correctChoice", question), myQuestion)
                } else if (JsonUtils.getString("correctChoice", question) == JsonUtils.getString("id", res)) {
                    myQuestion?.correctChoice = mutableListOf()
                    myQuestion?.correctChoice?.add(JsonUtils.getString("res", res))
                }
            }
        }

        fun serializeQuestions(question: List<ExamQuestion>): JsonArray {
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
