package org.ole.planet.myplanet.data.room.entity.legacy

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room row for exam questions formerly represented by RealmExamQuestion. */
@Entity(tableName = "exam_questions", indices = [Index("examId")])
data class RoomQuestionEntity(
    @PrimaryKey val id: String,
    val examId: String? = null,
    val type: String? = null,
    val question: String? = null,
    val choices: List<String>? = null,
    val correctChoice: List<String>? = null,
    val grade: Int = 0,
    val order: Int = 0,
)
