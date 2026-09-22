package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

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
                id = (if (examId.isNullOrEmpty()) parentId else examId).orEmpty()
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
            myExam.isFromNation = !parentId.isNullOrEmpty()
            myExam.teamId = JsonUtils.getString("teamId", exam)
            myExam.isTeamShareAllowed = JsonUtils.getBoolean("teamShareAllowed", exam)
            myExam.sourceSurveyId = JsonUtils.getString("sourceSurveyId", exam)
            return myExam
        }

        private fun checkIdsAndInsert(myCoursesID: String?, stepId: String?, myExam: StepExam?) {
            if (!myCoursesID.isNullOrEmpty()) {
                myExam?.courseId = myCoursesID
            }
            if (!stepId.isNullOrEmpty()) {
                myExam?.stepId = stepId
            }
        }

        fun serializeExam(exam: StepExam, questions: List<ExamQuestion>): JsonObject {
            val `object` = buildJsonObject {
                put("_id", exam.id)
                if (exam._rev != null) {
                    put("_rev", exam._rev)
                }
                put("name", exam.name)
                put("description", exam.description)
                put("passingPercentage", exam.passingPercentage)
                put("type", exam.type)
                put("updatedDate", exam.updatedDate)
                put("createdDate", exam.createdDate)
                put("adoptionDate", exam.adoptionDate)
                put("sourcePlanet", exam.sourcePlanet)
                put("totalMarks", exam.totalMarks)
                put("createdBy", exam.createdBy)
                if (exam.sourceSurveyId != null) {
                    put("sourceSurveyId", exam.sourceSurveyId)
                }
                if (exam.teamId != null) {
                    put("teamId", exam.teamId)
                }
                put("questions", ExamQuestion.serializeQuestions(questions).toKotlinx())
            }.toGson()
            `object`.addDocumentOrigin()
            return `object`
        }
    }
}
