package org.ole.planet.myplanet.data.room.entity.legacy

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room row for submission answers formerly represented by Answer. */
@Entity(tableName = "answers", indices = [Index("examId"), Index("questionId"), Index("submissionId")])
data class RoomAnswerEntity(
    @PrimaryKey val id: String,
    val value: String? = null,
    val valueChoices: List<String>? = null,
    val mistakes: Int = 0,
    val isPassed: Boolean = false,
    val grade: Int = 0,
    val examId: String? = null,
    val questionId: String? = null,
    val submissionId: String? = null,
)
