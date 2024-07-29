package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.model.RealmNews.Companion.newsDataList
import org.ole.planet.myplanet.utilities.JsonUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException

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
        val examDataList: MutableList<Array<String>> = mutableListOf()

        @JvmStatic
        fun insertCourseStepsExams(myCoursesID: String?, step_id: String?, exam: JsonObject, mRealm: Realm) {
            insertCourseStepsExams(myCoursesID, step_id, exam, "", mRealm)
        }

        @JvmStatic
        fun insertCourseStepsExams(myCoursesID: String?, step_id: String?, exam: JsonObject, parentId: String?, mRealm: Realm) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            var myExam = mRealm.where(RealmStepExam::class.java).equalTo("id", JsonUtils.getString("_id", exam)).findFirst()
            if (myExam == null) {
                val id = JsonUtils.getString("_id", exam)
                myExam = mRealm.createObject(RealmStepExam::class.java,
                    if (TextUtils.isEmpty(id)) {
                        parentId
                    } else {
                        id
                    }
                )
            }
            checkIdsAndInsert(myCoursesID, step_id, myExam)
            myExam?.type = if (exam.has("type")) JsonUtils.getString("type", exam) else "exam"
            myExam?.name = JsonUtils.getString("name", exam)
            myExam?.passingPercentage = JsonUtils.getString("passingPercentage", exam)
            myExam?._rev = JsonUtils.getString("_rev", exam)
            myExam?.createdBy = JsonUtils.getString("createdBy", exam)
            myExam?.sourcePlanet = JsonUtils.getString("sourcePlanet", exam)
            myExam?.createdDate = JsonUtils.getLong("createdDate", exam)
            myExam?.updatedDate = JsonUtils.getLong("updatedDate", exam)
            myExam?.totalMarks = JsonUtils.getInt("totalMarks", exam)
            myExam?.noOfQuestions = JsonUtils.getJsonArray("questions", exam).size()
            myExam?.isFromNation = !TextUtils.isEmpty(parentId)
            val oldQuestions: RealmResults<*>? = mRealm.where(RealmExamQuestion::class.java).equalTo("examId", JsonUtils.getString("_id", exam)).findAll()
            if (oldQuestions == null || oldQuestions.isEmpty()) {
                RealmExamQuestion.insertExamQuestions(
                    JsonUtils.getJsonArray("questions", exam),
                    JsonUtils.getString("_id", exam), mRealm
                )
            }
            mRealm.commitTransaction()

            val csvRow = arrayOf(
                JsonUtils.getString("_id", exam),
                JsonUtils.getString("_rev", exam),
                JsonUtils.getString("name", exam),
                JsonUtils.getString("passingPercentage", exam),
                JsonUtils.getString("type", exam),
                JsonUtils.getString("createdBy", exam),
                JsonUtils.getString("sourcePlanet", exam),
                JsonUtils.getString("createdDate", exam),
                JsonUtils.getString("updatedDate", exam),
                JsonUtils.getString("totalMarks", exam),
                JsonUtils.getString("noOfQuestions", exam),
                JsonUtils.getString("isFromNation", exam)
            )
            examDataList.add(csvRow)
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(arrayOf("_id", "_rev", "name", "passingPercentage", "type", "createdBy", "sourcePlanet", "createdDate", "updatedDate", "totalMarks", "noOfQuestions", "isFromNation"))
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun stepExamWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/stepExam.csv", examDataList)
        }

        private fun checkIdsAndInsert(myCoursesID: String?, step_id: String?, myExam: RealmStepExam?) {
            if (!TextUtils.isEmpty(myCoursesID)) {
                myExam?.courseId = myCoursesID
            }
            if (!TextUtils.isEmpty(step_id)) {
                myExam?.stepId = step_id
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
            `object`.add("questions", RealmExamQuestion.serializeQuestions(question))
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
