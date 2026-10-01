package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "answers", indices = [Index("examId"), Index("questionId"), Index("submissionId")])
open class Answer(
    @PrimaryKey @JvmField var id: String = "",
    var value: String? = null,
    var valueChoices: List<String>? = null,
    var mistakes: Int = 0,
    var isPassed: Boolean = false,
    var grade: Int = 0,
    var examId: String? = null,
    var questionId: String? = null,
    var submissionId: String? = null
) {
    companion object
}
