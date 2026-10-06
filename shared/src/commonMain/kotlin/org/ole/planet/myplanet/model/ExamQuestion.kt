package org.ole.planet.myplanet.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
    fun getCorrectChoice(): List<String>? {
        return correctChoiceList
    }

    fun setCorrectChoices(choices: List<String>?) {
        correctChoiceList = choices?.toList()
    }

    companion object
}
