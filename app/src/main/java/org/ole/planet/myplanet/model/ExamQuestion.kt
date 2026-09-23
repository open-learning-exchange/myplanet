package org.ole.planet.myplanet.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Locale
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

@Entity(tableName = "exam_questions", indices = [Index("examId")])
open class ExamQuestion(
    @PrimaryKey @JvmField var id: String = "",
    var header: String? = null,
    @ColumnInfo(name = "question") var body: String? = null,
    var type: String? = null,
    var examId: String? = null,
    // Persisted as a JSON list column (via the List<String> converter). Formerly a Room mirror
    // column; keeping it persisted preserves exam grading after a DB round-trip. Exposed through
    // getCorrectChoice()/setCorrectChoices() so callers are unaffected by the field name.
    var correctChoiceList: List<String>? = null,
    var marks: String? = null,
    var choices: String? = null,
    var hasOtherOption: Boolean = false,
    var scaleMax: Int = 9
) {
    private fun setCorrectChoiceArray(array: JsonArray, question: ExamQuestion?) {
        if (question == null) return
        val list = question.correctChoiceList?.toMutableList() ?: mutableListOf()
        val defaultLocale = Locale.getDefault()
        for (i in 0 until array.size()) {
            list.add(GsonUtils.getString(array, i).lowercase(defaultLocale))
        }
        question.correctChoiceList = list
    }

    fun getCorrectChoice(): List<String>? {
        return correctChoiceList
    }

    fun setCorrectChoices(choices: List<String>?) {
        correctChoiceList = choices?.toList()
    }

    @get:Ignore
    val correctChoiceArray: JsonArray
        get() = buildJsonArray {
            for (s in correctChoiceList ?: emptyList()) {
                add(s)
            }
        }.toGson()

    companion object {
        fun insertExamQuestions(questions: JsonArray, examId: String?): List<ExamQuestion> {
            if (questions.isEmpty()) return emptyList()

            val questionsToInsert = mutableListOf<ExamQuestion>()

            for (i in 0 until questions.size()) {
                val question = questions[i].asJsonObject
                val questionId = if (question.has("id")) {
                    GsonUtils.getString("id", question)
                } else {
                    "$examId-${i}"
                }

                val myQuestion = ExamQuestion().apply {
                    this.id = questionId
                    this.examId = examId
                    body = GsonUtils.getString("body", question)
                    type = GsonUtils.getString("type", question)
                    header = GsonUtils.getString("title", question)
                    marks = GsonUtils.getString("marks", question)
                    choices = if (question.has("choices")) {
                        GsonUtils.gson.toJson(GsonUtils.getJsonArray("choices", question))
                    } else {
                        "[]"
                    }

                    hasOtherOption = GsonUtils.getBoolean("hasOtherOption", question)
                    scaleMax = GsonUtils.getInt("scaleMax", question).let { if (it <= 0) 9 else it }
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
            if (question.has("correctChoice") && question["correctChoice"].isJsonArray) {
                myQuestion?.correctChoiceList = mutableListOf()
                myQuestion?.setCorrectChoiceArray(GsonUtils.getJsonArray("correctChoice", question), myQuestion)
            } else {
                val correctChoiceId = GsonUtils.getString("correctChoice", question)
                for (a in 0 until array.size()) {
                    val res = array[a].asJsonObject
                    if (correctChoiceId == GsonUtils.getString("id", res)) {
                        myQuestion?.correctChoiceList = listOf(GsonUtils.getString("res", res))
                        break
                    }
                }
            }
        }

        fun serializeQuestions(question: List<ExamQuestion>): JsonArray = buildJsonArray {
            for (que in question) {
                add(buildJsonObject {
                    put("header", que.header)
                    put("body", que.body)
                    put("type", que.type)
                    put("marks", que.marks)
                    put("choices", GsonUtils.getStringAsJsonArray(que.choices).toKotlinx())
                    put("correctChoice", que.correctChoiceArray.toKotlinx())
                    put("hasOtherOption", que.hasOtherOption)
                })
            }
        }.toGson()
    }
}
