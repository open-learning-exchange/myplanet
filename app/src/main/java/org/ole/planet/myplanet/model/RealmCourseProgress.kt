package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.JsonUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException

open class RealmCourseProgress : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var createdOn: String? = null
    var createdDate: Long = 0
    var updatedDate: Long = 0
    var _rev: String? = null
    var stepNum = 0
    var passed = false
    var userId: String? = null
    var courseId: String? = null
    var parentCode: String? = null

    companion object {
        val progressDataList: MutableList<Array<String>> = mutableListOf()
        fun serializeProgress(progress: RealmCourseProgress): JsonObject {
            val `object` = JsonObject()
            `object`.addProperty("userId", progress.userId)
            `object`.addProperty("parentCode", progress.parentCode)
            `object`.addProperty("courseId", progress.courseId)
            `object`.addProperty("passed", progress.passed)
            `object`.addProperty("stepNum", progress.stepNum)
            `object`.addProperty("createdOn", progress.createdOn)
            `object`.addProperty("createdDate", progress.createdDate)
            `object`.addProperty("updatedDate", progress.updatedDate)
            return `object`
        }

        fun getCourseProgress(mRealm: Realm, userId: String?): HashMap<String?, JsonObject> {
            val r = RealmMyCourse.getMyCourseByUserId(userId, mRealm.where(RealmMyCourse::class.java).findAll())
            val map = HashMap<String?, JsonObject>()
            for (course in r) {
                val `object` = JsonObject()
                val steps = RealmMyCourse.getCourseSteps(mRealm, course.courseId)
                `object`.addProperty("max", steps.size)
                `object`.addProperty("current", getCurrentProgress(steps, mRealm, userId, course.courseId))
                if (RealmMyCourse.isMyCourse(userId, course.courseId, mRealm)) map[course.courseId] = `object`
            }
            return map
        }

//        fun getPassedCourses(mRealm: Realm, userId: String?): List<RealmSubmission> {
//            val progresses = mRealm.where(RealmCourseProgress::class.java).equalTo("userId", userId).equalTo("passed", true).findAll()
//            val list: MutableList<RealmSubmission> = ArrayList()
//            for (progress in progresses) {
//                Utilities.log("Course id  certified " + progress.courseId)
//                val sub = progress.courseId?.let {
//                    mRealm.where(RealmSubmission::class.java)
//                        .contains("parentId", it).equalTo("userId", userId)
//                        .sort("lastUpdateTime", Sort.DESCENDING).findFirst()
//                }
//                if (sub != null) list.add(sub)
//            }
//            return list
//        }

        fun getCurrentProgress(steps: List<RealmCourseStep?>?, mRealm: Realm, userId: String?, courseId: String?): Int {
            var i = 0
            while (i < (steps?.size ?: 0)) {
                mRealm.where(RealmCourseProgress::class.java).equalTo("stepNum", i + 1).equalTo("userId", userId).equalTo("courseId", courseId)
                    .findFirst()
                    ?: break
                i++
            }
            return i
        }

        fun insert(mRealm: Realm, act: JsonObject?) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            var courseProgress = mRealm.where(RealmCourseProgress::class.java).equalTo("_id", JsonUtils.getString("_id", act)).findFirst()
            if (courseProgress == null) {
                courseProgress = mRealm.createObject(RealmCourseProgress::class.java, JsonUtils.getString("_id", act))
            }
            courseProgress?._rev = JsonUtils.getString("_rev", act)
            courseProgress?._id = JsonUtils.getString("_id", act)
            courseProgress?.passed = JsonUtils.getBoolean("passed", act)
            courseProgress?.stepNum = JsonUtils.getInt("stepNum", act)
            courseProgress?.userId = JsonUtils.getString("userId", act)
            courseProgress?.parentCode = JsonUtils.getString("parentCode", act)
            courseProgress?.courseId = JsonUtils.getString("courseId", act)
            courseProgress?.createdOn = JsonUtils.getString("createdOn", act)
            courseProgress?.createdDate = JsonUtils.getLong("createdDate", act)
            courseProgress?.updatedDate = JsonUtils.getLong("updatedDate", act)
            mRealm.commitTransaction()

            val csvRow = arrayOf(
                JsonUtils.getString("_id", act),
                JsonUtils.getString("_rev", act),
                JsonUtils.getBoolean("passed", act).toString(),
                JsonUtils.getInt("stepNum", act).toString(),
                JsonUtils.getString("userId", act),
                JsonUtils.getString("parentCode", act),
                JsonUtils.getString("courseId", act),
                JsonUtils.getString("createdOn", act),
                JsonUtils.getLong("createdDate", act).toString(),
                JsonUtils.getLong("updatedDate", act).toString()
            )
            progressDataList.add(csvRow)
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(arrayOf("progressId", "progress_rev", "passed", "stepNum", "userId", "parentCode", "courseId", "createdOn", "createdDate", "updatedDate"))
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun progressWriteCsv() {
            writeCsv("${MainApplication.context.getExternalFilesDir(null)}/ole/chatHistory.csv", progressDataList)
        }
    }
}
