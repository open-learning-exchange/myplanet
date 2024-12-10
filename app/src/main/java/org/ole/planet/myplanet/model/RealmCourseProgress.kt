package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getCourseSteps
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getMyCourseByUserId
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.isMyCourse
import org.ole.planet.myplanet.utilities.JsonUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException

class RealmCourseProgress : RealmObject {
    @PrimaryKey
    var id: String = ""
    var _id: String = ""
    var createdOn: String = ""
    var createdDate: Long = 0
    var updatedDate: Long = 0
    var _rev: String = ""
    var stepNum: Int = 0
    var passed: Boolean = false
    var userId: String = ""
    var courseId: String = ""
    var parentCode: String = ""

    companion object {
        private val progressDataList = mutableListOf<Array<String>>()

        fun serializeProgress(progress: RealmCourseProgress): JsonObject {
            return JsonObject().apply {
                addProperty("userId", progress.userId)
                addProperty("parentCode", progress.parentCode)
                addProperty("courseId", progress.courseId)
                addProperty("passed", progress.passed)
                addProperty("stepNum", progress.stepNum)
                addProperty("createdOn", progress.createdOn)
                addProperty("createdDate", progress.createdDate)
                addProperty("updatedDate", progress.updatedDate)
            }
        }

        suspend fun getCourseProgress(realm: Realm, userId: String): Flow<Map<String, JsonObject>> {
            return getMyCourseByUserId(userId).map { courses ->
                courses.map { course ->
                    getCourseSteps(course.courseId).first().let { steps ->
                        if (isMyCourse(userId, course.courseId)) {
                            course.courseId to JsonObject().apply {
                                addProperty("max", steps.size)
                                addProperty("current", getCurrentProgress(steps, realm, userId, course.courseId))
                            }
                        } else null
                    }
                }.filterNotNull().toMap()
            }
        }

//        fun getPassedCourses(realm: Realm, userId: String): Flow<List<RealmSubmission>> {
//            return realm.query<RealmCourseProgress>("userId == $0 AND passed == true", userId)
//                .asFlow()
//                .map { progresses ->
//                    progresses.list.mapNotNull { progress ->
//                        realm.query<RealmSubmission>("parentId CONTAINS $0 AND userId == $1",
//                            progress.courseId, userId).sort("lastUpdateTime", Sort.DESCENDING)
//                            .first().find()
//                    }
//                }
//        }

        fun getCurrentProgress(steps: List<RealmCourseStep>, realm: Realm, userId: String, courseId: String): Int {
            var currentStep = 0
            while (currentStep < steps.size) {
                val progress = realm.query<RealmCourseProgress>(
                    "stepNum == $0 AND userId == $1 AND courseId == $2", currentStep + 1, userId, courseId
                ).first().find() ?: break
                currentStep++
            }
            return currentStep
        }

        suspend fun insert(realm: Realm, act: JsonObject) {
            realm.write {
                val courseProgress = query<RealmCourseProgress>("_id == $0",
                    JsonUtils.getString("_id", act)).first().find()
                    ?: RealmCourseProgress().apply {
                        id = JsonUtils.getString("_id", act)
                    }

                copyToRealm(courseProgress.apply {
                    _rev = JsonUtils.getString("_rev", act)
                    _id = JsonUtils.getString("_id", act)
                    passed = JsonUtils.getBoolean("passed", act)
                    stepNum = JsonUtils.getInt("stepNum", act)
                    userId = JsonUtils.getString("userId", act)
                    parentCode = JsonUtils.getString("parentCode", act)
                    courseId = JsonUtils.getString("courseId", act)
                    createdOn = JsonUtils.getString("createdOn", act)
                    createdDate = JsonUtils.getLong("createdDate", act)
                    updatedDate = JsonUtils.getLong("updatedDate", act)
                })

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
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                CSVWriter(FileWriter(file)).use { writer ->
                    writer.writeNext(arrayOf(
                        "progressId", "progress_rev", "passed", "stepNum", "userId", "parentCode",
                        "courseId", "createdOn", "createdDate", "updatedDate"
                    ))
                    data.forEach { row ->
                        writer.writeNext(row)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun progressWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/chatHistory.csv", progressDataList)
        }
    }
}
