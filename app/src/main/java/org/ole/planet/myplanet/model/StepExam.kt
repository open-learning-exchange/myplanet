package org.ole.planet.myplanet.model
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

import android.text.TextUtils
import com.google.gson.JsonObject
import org.ole.planet.myplanet.utils.JsonUtils

@Entity(tableName = "exams", indices = [Index("courseId"), Index("stepId"), Index("teamId"), Index("sourceSurveyId")])
open class StepExam(
    @PrimaryKey @JvmField var id: String = "",
    var _rev: String? = null,
    var createdDate: Long = 0,
    var updatedDate: Long = 0,
    var adoptionDate: Long = 0,
    var createdBy: String? = null,
    var totalMarks: Int = 0,
    var name: String? = null,
    var description: String? = null,
    var type: String? = null,
    var stepId: String? = null,
    var courseId: String? = null,
    var sourcePlanet: String? = null,
    var passingPercentage: String? = null,
    var noOfQuestions: Int = 0,
    var isFromNation: Boolean = false,
    var teamId: String? = null,
    var isTeamShareAllowed: Boolean = false,
    var sourceSurveyId: String? = null
) {
    companion object {
        fun insertCourseStepsExams(myCoursesID: String?, stepId: String?, exam: JsonObject): StepExam {
            return insertCourseStepsExams(myCoursesID, stepId, exam, "")
        }

        fun insertCourseStepsExams(myCoursesID: String?, stepId: String?, exam: JsonObject, parentId: String?): StepExam {
            val examId = JsonUtils.getString("_id", exam)
            val myExam = StepExam().apply {
                id = (if (TextUtils.isEmpty(examId)) parentId else examId).orEmpty()
            }
            checkIdsAndInsert(myCoursesID, stepId, myExam)
            myExam.type = if (exam.has("type")) JsonUtils.getString("type", exam) else "exam"
            myExam.name = JsonUtils.getString("name", exam)
            myExam.description = JsonUtils.getString("description", exam)
            myExam.passingPercentage = JsonUtils.getString("passingPercentage", exam)
            myExam._rev = JsonUtils.getString("_rev", exam)
            myExam.createdBy = JsonUtils.getString("createdBy", exam)
            myExam.sourcePlanet = JsonUtils.getString("sourcePlanet", exam)
            myExam.createdDate = JsonUtils.getLong("createdDate", exam)
            myExam.updatedDate = JsonUtils.getLong("updatedDate", exam)
            myExam.adoptionDate = JsonUtils.getLong("adoptionDate", exam)
            myExam.totalMarks = JsonUtils.getInt("totalMarks", exam)
            myExam.noOfQuestions = JsonUtils.getJsonArray("questions", exam).size()
            myExam.isFromNation = !TextUtils.isEmpty(parentId)
            myExam.teamId = JsonUtils.getString("teamId", exam)
            myExam.isTeamShareAllowed = JsonUtils.getBoolean("teamShareAllowed", exam)
            myExam.sourceSurveyId = JsonUtils.getString("sourceSurveyId", exam)
            return myExam
        }

        private fun checkIdsAndInsert(myCoursesID: String?, stepId: String?, myExam: StepExam?) {
            if (!TextUtils.isEmpty(myCoursesID)) {
                myExam?.courseId = myCoursesID
            }
            if (!TextUtils.isEmpty(stepId)) {
                myExam?.stepId = stepId
            }
        }

        fun serializeExam(exam: StepExam, questions: List<ExamQuestion>): JsonObject {
            val `object` = JsonObject()
            `object`.addProperty("_id", exam.id)
            if (exam._rev != null) {
                `object`.addProperty("_rev", exam._rev)
            }
            `object`.addProperty("name", exam.name)
            `object`.addProperty("description", exam.description)
            `object`.addProperty("passingPercentage", exam.passingPercentage)
            `object`.addProperty("type", exam.type)
            `object`.addProperty("updatedDate", exam.updatedDate)
            `object`.addProperty("createdDate", exam.createdDate)
            `object`.addProperty("adoptionDate", exam.adoptionDate)
            `object`.addProperty("sourcePlanet", exam.sourcePlanet)
            `object`.addProperty("totalMarks", exam.totalMarks)
            `object`.addProperty("createdBy", exam.createdBy)
            if (exam.sourceSurveyId != null) {
                `object`.addProperty("sourceSurveyId", exam.sourceSurveyId)
            }
            if (exam.teamId != null) {
                `object`.addProperty("teamId", exam.teamId)
            }
            `object`.add("questions", ExamQuestion.serializeQuestions(questions))
            return `object`
        }
    }
}
