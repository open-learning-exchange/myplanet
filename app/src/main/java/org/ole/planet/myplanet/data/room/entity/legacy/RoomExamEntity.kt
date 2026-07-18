package org.ole.planet.myplanet.data.room.entity.legacy

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room row for exams and surveys formerly represented by RealmStepExam. */
@Entity(tableName = "exams", indices = [Index("courseId"), Index("stepId"), Index("teamId"), Index("sourceSurveyId")])
data class RoomExamEntity(
    @PrimaryKey val id: String,
    val _rev: String? = null,
    val createdDate: Long = 0,
    val updatedDate: Long = 0,
    val adoptionDate: Long = 0,
    val createdBy: String? = null,
    val totalMarks: Int = 0,
    val name: String? = null,
    val description: String? = null,
    val type: String? = null,
    val stepId: String? = null,
    val courseId: String? = null,
    val sourcePlanet: String? = null,
    val passingPercentage: String? = null,
    val noOfQuestions: Int = 0,
    val isFromNation: Boolean = false,
    val teamId: String? = null,
    val isTeamShareAllowed: Boolean = false,
    val sourceSurveyId: String? = null,
)
