package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.JsonObject
import org.ole.planet.myplanet.utils.JsonUtils

open class RealmStepExam {
    var id: String? = null
    var _rev: String? = null
    var createdDate: Long = 0
    var updatedDate: Long = 0
    var adoptionDate: Long = 0
    var createdBy: String? = null
    var totalMarks = 0
    var name: String? = null
    var description: String? = null
    var type: String? = null
    var stepId: String? = null
    var courseId: String? = null
    var sourcePlanet: String? = null
    var passingPercentage: String? = null
    var noOfQuestions = 0
    var isFromNation = false
    var teamId: String? = null
    var isTeamShareAllowed = false
    var sourceSurveyId: String? = null

    companion object {
        fun insertCourseStepsExams(myCoursesID: String?, stepId: String?, exam: JsonObject): RealmStepExam {
            return insertCourseStepsExams(myCoursesID, stepId, exam, "")
        }

        fun insertCourseStepsExams(myCoursesID: String?, stepId: String?, exam: JsonObject, parentId: String?): RealmStepExam {
            val examId = JsonUtils.getString("_id", exam)
            val myExam = RealmStepExam().apply {
                id = if (TextUtils.isEmpty(examId)) parentId else examId
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

        private fun checkIdsAndInsert(myCoursesID: String?, stepId: String?, myExam: RealmStepExam?) {
            if (!TextUtils.isEmpty(myCoursesID)) {
                myExam?.courseId = myCoursesID
            }
            if (!TextUtils.isEmpty(stepId)) {
                myExam?.stepId = stepId
            }
        }

        fun serializeExam(exam: RealmStepExam, questions: List<RealmExamQuestion>): JsonObject {
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
            `object`.add("questions", RealmExamQuestion.serializeQuestions(questions))
            return `object`
        }
    }
}
