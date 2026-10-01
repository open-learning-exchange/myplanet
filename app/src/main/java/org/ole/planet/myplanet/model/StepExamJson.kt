package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

// App-side Gson helpers for StepExam, kept out of the Room entity.

fun StepExam.Companion.insertCourseStepsExams(myCoursesID: String?, stepId: String?, exam: JsonObject): StepExam {
    return insertCourseStepsExams(myCoursesID, stepId, exam, "")
}

fun StepExam.Companion.insertCourseStepsExams(myCoursesID: String?, stepId: String?, exam: JsonObject, parentId: String?): StepExam {
    val shallow = JsonObject().apply { exam.entrySet().forEach { (k, v) -> if (k != "questions") add(k, v) } }
    val kExam = shallow.toKotlinx().jsonObject
    val examId = JsonUtils.getString("_id", kExam)
    val myExam = StepExam().apply {
        id = (if (examId.isNullOrEmpty()) parentId else examId).orEmpty()
    }
    checkIdsAndInsert(myCoursesID, stepId, myExam)
    myExam.type = if (kExam.containsKey("type")) JsonUtils.getString("type", kExam) else "exam"
    myExam.name = JsonUtils.getString("name", kExam)
    myExam.description = JsonUtils.getString("description", kExam)
    myExam.passingPercentage = JsonUtils.getString("passingPercentage", kExam)
    myExam._rev = JsonUtils.getString("_rev", kExam)
    myExam.createdBy = JsonUtils.getString("createdBy", kExam)
    myExam.sourcePlanet = JsonUtils.getString("sourcePlanet", kExam)
    myExam.createdDate = JsonUtils.getLong("createdDate", kExam)
    myExam.updatedDate = JsonUtils.getLong("updatedDate", kExam)
    myExam.adoptionDate = JsonUtils.getLong("adoptionDate", kExam)
    myExam.totalMarks = JsonUtils.getInt("totalMarks", kExam)
    myExam.noOfQuestions = (exam.get("questions") as? com.google.gson.JsonArray)?.size() ?: 0
    myExam.isFromNation = !parentId.isNullOrEmpty()
    myExam.teamId = JsonUtils.getString("teamId", kExam)
    myExam.isTeamShareAllowed = JsonUtils.getBoolean("teamShareAllowed", kExam)
    myExam.sourceSurveyId = JsonUtils.getString("sourceSurveyId", kExam)
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

fun StepExam.Companion.serializeExam(exam: StepExam, questions: List<ExamQuestion>): JsonObject {
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
