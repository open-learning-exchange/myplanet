package org.ole.planet.myplanet.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*

class RealmSubmission : RealmObject {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var _rev: String? = null
    var parentId: String? = null
    var type: String? = null
    var userId: String? = null
    var user: String? = null
    var startTime: Long = 0
    var lastUpdateTime: Long = 0
    var answers: RealmList<RealmAnswer> = realmListOf()
    var grade: Long = 0
    var status: String? = null
    var uploaded: Boolean = false
    var sender: String? = null
    var source: String? = null
    var parentCode: String? = null
    var parent: String? = null

    companion object {
        private val submissionDataList: MutableList<Array<String>> = mutableListOf()

        suspend fun insert(realm: Realm, submission: JsonObject) {
            if (submission.has("_attachments")) {
                return
            }

            val id = JsonUtils.getString("_id", submission)
            var parent: JsonObject? = null
            var parentId: String? = null

            realm.write {
                val existingSubmission = query<RealmSubmission>("_id == $0", id).first().find()
                val sub = existingSubmission ?: copyToRealm(RealmSubmission().apply { _id = id })

                sub._id = JsonUtils.getString("_id", submission)
                sub.status = JsonUtils.getString("status", submission)
                sub._rev = JsonUtils.getString("_rev", submission)
                sub.grade = JsonUtils.getLong("grade", submission)
                sub.type = JsonUtils.getString("type", submission)
                sub.uploaded = JsonUtils.getString("status", submission) == "graded"
                sub.startTime = JsonUtils.getLong("startTime", submission)
                sub.lastUpdateTime = JsonUtils.getLong("lastUpdateTime", submission)
                sub.parentId = JsonUtils.getString("parentId", submission)
                sub.sender = JsonUtils.getString("sender", submission)
                sub.source = JsonUtils.getString("source", submission)
                sub.parentCode = JsonUtils.getString("parentCode", submission)
                sub.parent = Gson().toJson(JsonUtils.getJsonObject("parent", submission))
                sub.user = Gson().toJson(JsonUtils.getJsonObject("user", submission))

                parent = JsonUtils.getJsonObject("parent", submission)
                parentId = JsonUtils.getString("parentId", submission)

                val csvRow = arrayOf(
                    JsonUtils.getString("_id", submission),
                    JsonUtils.getString("parentId", submission),
                    JsonUtils.getString("type", submission),
                    JsonUtils.getString("status", submission),
                    JsonUtils.getString("grade", submission),
                    JsonUtils.getString("startTime", submission),
                    JsonUtils.getString("lastUpdateTime", submission),
                    JsonUtils.getString("sender", submission),
                    JsonUtils.getString("source", submission),
                    JsonUtils.getString("parentCode", submission),
                    JsonUtils.getString("user", submission)
                )
                submissionDataList.add(csvRow)

                val userId = JsonUtils.getString("_id", JsonUtils.getJsonObject("user", submission))
                sub.userId = if (userId.contains("@")) {
                    val us = userId.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (us[0].startsWith("org.couchdb.user:")) {
                        us[0]
                    } else {
                        "org.couchdb.user:${us[0]}"
                    }
                } else {
                    userId
                }
            }

            if (parent != null && parentId != null) {
                RealmStepExam.insertCourseStepsExams("", "", parent, realm, parentId)
            }
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                CSVWriter(FileWriter(file)).use { writer ->
                    writer.writeNext(arrayOf("_id", "parentId", "type", "status", "grade",
                        "startTime", "lastUpdateTime", "sender", "source", "parentCode", "user")
                    )
                    data.forEach { writer.writeNext(it) }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun submissionWriteCsv() {
            val filePath = "${MainApplication.context.getExternalFilesDir(null)}/ole/submission.csv"
            writeCsv(filePath, submissionDataList)
        }

        private fun serializeExamResult(realm: Realm, sub: RealmSubmission): JsonObject {
            val jsonObject = JsonObject()
            val user = realm.query<RealmUserModel>("id == $0", sub.userId).first().find()
            val parentId = sub.parentId
            val examId = if (parentId?.contains("@") == true) parentId.split("@")[0] else parentId
            val exam = realm.query<RealmStepExam>("id == $0", examId).first().find()

            jsonObject.addProperty("_id", sub._id)
            jsonObject.addProperty("_rev", sub._rev)
            jsonObject.addProperty("parentId", sub.parentId)
            jsonObject.addProperty("type", sub.type)
            jsonObject.addProperty("grade", sub.grade)
            jsonObject.addProperty("startTime", sub.startTime)
            jsonObject.addProperty("lastUpdateTime", sub.lastUpdateTime)
            jsonObject.addProperty("status", sub.status)
            jsonObject.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            jsonObject.addProperty("deviceName", NetworkUtils.getDeviceName())
            jsonObject.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context))
            jsonObject.addProperty("sender", sub.sender)
            jsonObject.addProperty("source", sub.source)
            jsonObject.addProperty("parentCode", sub.parentCode)
            jsonObject.add("parent", sub.parent?.let { Gson().fromJson(it, JsonObject::class.java) })
            jsonObject.add("answers", RealmAnswer.serializeRealmAnswer(sub.answers))

            if (exam != null && sub.parent.isNullOrEmpty()) {
                jsonObject.add("parent", RealmStepExam.serializeExam(realm, exam))
            }

            if (sub.user.isNullOrEmpty()) {
                jsonObject.add("user", user?.serialize())
            } else {
                jsonObject.add("user", JsonParser.parseString(sub.user))
            }

            return jsonObject
        }

        fun isStepCompleted(realm: Realm, id: String?, userId: String?): Boolean {
            val stepExam = realm.query<RealmStepExam>("stepId == $0", id).first().find() ?: return true
            return realm.query<RealmSubmission>("userId == $0 AND parentId == $1 AND status != 'pending'", userId, stepExam.id).first().find() != null
        }

        suspend fun createSubmission(sub: RealmSubmission?, realm: Realm): RealmSubmission {
            return realm.write {
                val newSub = sub ?: RealmSubmission().apply { id = UUID.randomUUID().toString() }
                findLatest(newSub) ?: copyToRealm(newSub)
            }
        }

        suspend fun continueResultUpload(sub: RealmSubmission, apiInterface: ApiInterface?, realm: Realm) {
            if (sub.userId.isNullOrEmpty() || sub.userId?.startsWith("guest") == true) return

            val serializedResult = serializeExamResult(realm, sub)
            val response: JsonObject? = if (sub._id.isNullOrEmpty()) {
                apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/submissions", serializedResult)?.execute()?.body()
            } else {
                apiInterface?.putDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/submissions/${sub._id}", serializedResult)?.execute()?.body()
            }
            
            if (response != null) {
                realm.write {
                    findLatest(sub)?.apply {
                        _id = JsonUtils.getString("id", response)
                        _rev = JsonUtils.getString("rev", response)
                    }
                }
            }
        }

        @JvmStatic
        fun getNoOfSubmissionByUser(id: String?, courseId: String?, userId: String?, mRealm: Realm): String {
            if (id == null || userId == null) return "No Submissions Found"
            val submissionParentId = generateParentId(courseId, id)
            if(submissionParentId.isNullOrEmpty()) return "No Submissions Found"

            val submissionCount = mRealm.query<RealmSubmission>(
                "parentId == $0 AND userId == $1 AND status == $2",
                submissionParentId, userId, "complete"
            ).count().find().toInt()

            val pluralizedString = if (submissionCount == 1) "time" else "times"
            return MainApplication.context.getString(R.string.survey_taken) + " " + submissionCount + " " + pluralizedString
        }

        @JvmStatic
        fun getNoOfSurveySubmissionByUser(userId: String?, mRealm: Realm): Int {
            if (userId == null) return 0
            return mRealm.query<RealmSubmission>(
                "userId == $0 AND type == $1 AND status CONTAINS[c] $2",
                userId, "survey", "pending"
            ).count().find().toInt()
        }

        fun getRecentSubmissionDate(realm: Realm, id: String?, userId: String?): String {
            val submission = realm.query<RealmSubmission>("parentId == $0 AND userId == $1", id, userId).sort("startTime", Sort.DESCENDING).first().find()
            return submission?.startTime?.let { TimeUtils.getFormatedDateWithTime(it) } ?: ""
        }

        fun getExamMap(realm: Realm, submissions: List<RealmSubmission>): Map<String?, RealmStepExam?> {
            return submissions.associate { submission ->
                val parentId = submission.parentId
                val examId = if (checkParentId(parentId)) parentId?.split("@")?.firstOrNull() else parentId
                val exam = realm.query<RealmStepExam>("id == $0", examId).first().find()
                submission.parentId to exam
            }
        }

        private fun checkParentId(parentId: String?): Boolean {
            return parentId?.contains("@") == true
        }
    }
}
