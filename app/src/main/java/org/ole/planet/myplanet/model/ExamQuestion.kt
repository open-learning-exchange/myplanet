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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.KotlinxJsonUtils
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx
import kotlinx.serialization.json.JsonArray as KJsonArray
import kotlinx.serialization.json.JsonObject as KJsonObject

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
    private fun setCorrectChoiceArray(array: KJsonArray, question: ExamQuestion?) {
        if (question == null) return
        val list = question.correctChoiceList?.toMutableList() ?: mutableListOf()
        val defaultLocale = Locale.getDefault()
        for (i in array.indices) {
            list.add(KotlinxJsonUtils.getString(array, i).lowercase(defaultLocale))
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

            val kQuestions = questions.toKotlinx().jsonArray
            val questionsToInsert = mutableListOf<ExamQuestion>()

            for (i in kQuestions.indices) {
                val question = kQuestions[i].jsonObject
                val questionId = if (question.containsKey("id")) {
                    KotlinxJsonUtils.getString("id", question)
                } else {
                    "$examId-${i}"
                }

                val myQuestion = ExamQuestion().apply {
                    this.id = questionId
                    this.examId = examId
                    body = KotlinxJsonUtils.getString("body", question)
                    type = KotlinxJsonUtils.getString("type", question)
                    header = KotlinxJsonUtils.getString("title", question)
                    marks = KotlinxJsonUtils.getString("marks", question)
                    choices = if (question.containsKey("choices")) {
                        KotlinxJsonUtils.getJsonArray("choices", question).toString()
                    } else {
                        "[]"
                    }

                    hasOtherOption = KotlinxJsonUtils.getBoolean("hasOtherOption", question)
                    scaleMax = KotlinxJsonUtils.getInt("scaleMax", question).let { if (it <= 0) 9 else it }
                    val isMultipleChoice = type?.startsWith("select") == true && question.containsKey("choices")
                    if (isMultipleChoice) {
                        insertCorrectChoice(question.getValue("choices").jsonArray, question, this)
                    }
                }
                questionsToInsert.add(myQuestion)
            }
            return questionsToInsert
        }

        private fun insertCorrectChoice(array: KJsonArray, question: KJsonObject, myQuestion: ExamQuestion?) {
            if (question.containsKey("correctChoice") && question.getValue("correctChoice") is KJsonArray) {
                myQuestion?.correctChoiceList = mutableListOf()
                myQuestion?.setCorrectChoiceArray(KotlinxJsonUtils.getJsonArray("correctChoice", question), myQuestion)
            } else {
                val correctChoiceId = KotlinxJsonUtils.getString("correctChoice", question)
                for (a in array.indices) {
                    val res = array[a].jsonObject
                    if (correctChoiceId == KotlinxJsonUtils.getString("id", res)) {
                        myQuestion?.correctChoiceList = listOf(KotlinxJsonUtils.getString("res", res))
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
