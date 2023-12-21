package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities

open class RealmStepExam : RealmObject() {
    @JvmField
    @PrimaryKey
    var id: String? = null
    @JvmField
    var _rev: String? = null
    @JvmField
    var createdDate: Long = 0
    @JvmField
    var updatedDate: Long = 0
    @JvmField
    var createdBy: String? = null
    @JvmField
    var totalMarks = 0
    @JvmField
    var name: String? = null
    @JvmField
    var type: String? = null
    @JvmField
    var stepId: String? = null
    @JvmField
    var courseId: String? = null
    @JvmField
    var sourcePlanet: String? = null
    @JvmField
    var passingPercentage: String? = null
    @JvmField
    var noOfQuestions = 0
    @JvmField
    var isFromNation = false

    companion object {
        @JvmStatic
        fun insertCourseStepsExams(myCoursesID: String?, step_id: String?, exam: JsonObject, mRealm: Realm) {
            insertCourseStepsExams(myCoursesID, step_id, exam, "", mRealm)
        }

        @JvmStatic
        fun insertCourseStepsExams(myCoursesID: String?, step_id: String?, exam: JsonObject, parentId: String?, mRealm: Realm) {
            var myExam = mRealm.where(RealmStepExam::class.java).equalTo("id", JsonUtils.getString("_id", exam)).findFirst()
            if (myExam == null) {
                val id = JsonUtils.getString("_id", exam)
                myExam = mRealm.createObject(
                    RealmStepExam::class.java,
                    if (TextUtils.isEmpty(id)) parentId else id
                )
            }
            checkIdsAndInsert(myCoursesID, step_id, myExam)
            myExam!!.type = if (exam.has("type")) JsonUtils.getString("type", exam) else "exam"
            myExam.name = JsonUtils.getString("name", exam)
            myExam.passingPercentage = JsonUtils.getString("passingPercentage", exam)
            myExam._rev = JsonUtils.getString("_rev", exam)
            myExam.createdBy = JsonUtils.getString("createdBy", exam)
            myExam.sourcePlanet = JsonUtils.getString("sourcePlanet", exam)
            myExam.createdDate = JsonUtils.getLong("createdDate", exam)
            myExam.updatedDate = JsonUtils.getLong("updatedDate", exam)
            myExam.totalMarks = JsonUtils.getInt("totalMarks", exam)
            myExam.noOfQuestions = JsonUtils.getJsonArray("questions", exam).size()
            myExam.isFromNation = !TextUtils.isEmpty(parentId)
            val oldQuestions: RealmResults<*>? = mRealm.where(RealmExamQuestion::class.java).equalTo("examId", JsonUtils.getString("_id", exam)).findAll()
            if (oldQuestions == null || oldQuestions.isEmpty()) {
                RealmExamQuestion.insertExamQuestions(
                    JsonUtils.getJsonArray("questions", exam),
                    JsonUtils.getString("_id", exam),
                    mRealm
                )
            }
        }

        private fun checkIdsAndInsert(myCoursesID: String?, step_id: String?, myExam: RealmStepExam?) {
            if (!TextUtils.isEmpty(myCoursesID)) {
                myExam!!.courseId = myCoursesID
            }
            if (!TextUtils.isEmpty(step_id)) {
                myExam!!.stepId = step_id
            }
        }

        @JvmStatic
        fun getNoOfExam(mRealm: Realm, courseId: String?): Int {
            val res: RealmResults<*>? = mRealm.where(RealmStepExam::class.java).equalTo("courseId", courseId).findAll()
            return res?.size ?: 0
        }

        @JvmStatic
        fun serializeExam(mRealm: Realm, exam: RealmStepExam): JsonObject {
            val `object` = JsonObject()
            `object`.addProperty("_id", exam.id)
            `object`.addProperty("_rev", exam._rev)
            `object`.addProperty("name", exam.name)
            `object`.addProperty("passingPercentage", exam.passingPercentage)
            `object`.addProperty("type", exam.type)
            `object`.addProperty("updatedDate", exam.updatedDate)
            `object`.addProperty("createdDate", exam.createdDate)
            `object`.addProperty("sourcePlanet", exam.sourcePlanet)
            `object`.addProperty("totalMarks", exam.createdDate)
            `object`.addProperty("createdBy", exam.createdBy)
            val question = mRealm.where(RealmExamQuestion::class.java).equalTo("examId", exam.id).findAll()
            `object`.add("questions", RealmExamQuestion.serializeQuestions(mRealm, question))
            return `object`
        }

        @JvmStatic
        fun getIds(list: List<RealmStepExam>): Array<String?> {
            val ids = arrayOfNulls<String>(list.size)
            var i = 0
            for (e in list) {
                if (e.type!!.startsWith("survey")) ids[i] = e.id else ids[i] =
                    e.id + "@" + e.courseId
                i++
            }
            Utilities.log(Gson().toJson(ids))
            return ids
        }
    }
}
