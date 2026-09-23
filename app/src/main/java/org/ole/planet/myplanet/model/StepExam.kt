package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.GsonUtils
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
            val examId = GsonUtils.getString("_id", exam)
            val myExam = StepExam().apply {
                id = (if (examId.isNullOrEmpty()) parentId else examId).orEmpty()
            }
            checkIdsAndInsert(myCoursesID, stepId, myExam)
            myExam.type = if (exam.has("type")) GsonUtils.getString("type", exam) else "exam"
            myExam.name = GsonUtils.getString("name", exam)
            myExam.description = GsonUtils.getString("description", exam)
            myExam.passingPercentage = GsonUtils.getString("passingPercentage", exam)
            myExam._rev = GsonUtils.getString("_rev", exam)
            myExam.createdBy = GsonUtils.getString("createdBy", exam)
            myExam.sourcePlanet = GsonUtils.getString("sourcePlanet", exam)
            myExam.createdDate = GsonUtils.getLong("createdDate", exam)
            myExam.updatedDate = GsonUtils.getLong("updatedDate", exam)
            myExam.adoptionDate = GsonUtils.getLong("adoptionDate", exam)
            myExam.totalMarks = GsonUtils.getInt("totalMarks", exam)
            myExam.noOfQuestions = GsonUtils.getJsonArray("questions", exam).size()
            myExam.isFromNation = !parentId.isNullOrEmpty()
            myExam.teamId = GsonUtils.getString("teamId", exam)
            myExam.isTeamShareAllowed = GsonUtils.getBoolean("teamShareAllowed", exam)
            myExam.sourceSurveyId = GsonUtils.getString("sourceSurveyId", exam)
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
