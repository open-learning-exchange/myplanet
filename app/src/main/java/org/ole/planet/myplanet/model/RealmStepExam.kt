package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmStepExam : RealmObject() {
    @PrimaryKey
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

        @JvmStatic
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

        @JvmStatic
        fun getIds(list: List<RealmStepExam>): Array<String?> {
            val ids = arrayOfNulls<String>(list.size)
            for ((i, e) in list.withIndex()) {
                if (e.type?.startsWith("survey") == true) {
                    ids[i] = e.id
                } else {
                    ids[i] = e.id + "@" + e.courseId
                }
            }
            return ids
        }

        @JvmStatic
        fun getSurveyCreationTime(surveyId: String, mRealm: Realm): Long? {
            val survey = mRealm.where(RealmStepExam::class.java).equalTo("id", surveyId).findFirst()
            return survey?.createdDate
        }
    }
}
