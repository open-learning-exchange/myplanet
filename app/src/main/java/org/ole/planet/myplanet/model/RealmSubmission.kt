package org.ole.planet.myplanet.model

import android.content.Context
import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.realm.Case
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.Sort
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.io.IOException
import java.util.Date
import java.util.UUID

open class RealmSubmission : RealmObject() {
    @JvmField
    @PrimaryKey
    var id: String? = null
    @JvmField
    var _id: String? = null
    @JvmField
    var _rev: String? = null
    @JvmField
    var parentId: String? = null
    @JvmField
    var type: String? = null
    @JvmField
    var userId: String? = null
    @JvmField
    var user: String? = null
    @JvmField
    var startTime: Long = 0
    @JvmField
    var lastUpdateTime: Long = 0
    @JvmField
    var answers: RealmList<RealmAnswer>? = null
    @JvmField
    var grade: Long = 0
    @JvmField
    var status: String? = null
    @JvmField
    var uploaded = false
    @JvmField
    var sender: String? = null
    @JvmField
    var source: String? = null
    @JvmField
    var parentCode: String? = null
    @JvmField
    var parent: String? = null

    companion object {
        @JvmStatic
        fun insert(mRealm: Realm, submission: JsonObject) {
            if (submission.has("_attachments")) {
                return
            }
            val id = JsonUtils.getString("_id", submission)
            var sub = mRealm.where(RealmSubmission::class.java).equalTo("_id", id).findFirst()
            if (sub == null) {
                sub = mRealm.createObject(RealmSubmission::class.java, id)
            }
            if (sub != null) {
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
            }

            RealmStepExam.insertCourseStepsExams("", "",
                JsonUtils.getJsonObject("parent", submission),
                JsonUtils.getString("parentId", submission),
                mRealm
            )
            val userId = JsonUtils.getString("_id", JsonUtils.getJsonObject("user", submission))
            if (userId.contains("@")) {
                val us = userId.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (us[0].startsWith("org.couchdb.user:")) {
                    if (sub != null) {
                        sub.userId = us[0]
                    }
                } else {
                    if (sub != null) {
                        sub.userId = "org.couchdb.user:" + us[0]
                    }
                }
            } else {
                if (sub != null) {
                    sub.userId = userId
                }
            }
            Utilities.log("Insert sub $sub")
        }

        fun serializeExamResult(mRealm: Realm, sub: RealmSubmission, context: Context?): JsonObject {
            val `object` = JsonObject()
            val user = mRealm.where(RealmUserModel::class.java).equalTo("id", sub.userId).findFirst()
            var examId = sub.parentId
            if (sub.parentId!!.contains("@")) {
                examId = sub.parentId!!.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
            }
            val exam = mRealm.where(RealmStepExam::class.java).equalTo("id", examId).findFirst()
            if (!TextUtils.isEmpty(sub._id)) `object`.addProperty("_id", sub._id)
            if (!TextUtils.isEmpty(sub._rev)) `object`.addProperty("_rev", sub._rev)
            `object`.addProperty("parentId", sub.parentId)
            `object`.addProperty("type", sub.type)
            `object`.addProperty("grade", sub.grade)
            `object`.addProperty("startTime", sub.startTime)
            `object`.addProperty("lastUpdateTime", sub.lastUpdateTime)
            `object`.addProperty("status", sub.status)
            `object`.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            `object`.addProperty("deviceName", NetworkUtils.getDeviceName())
            `object`.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
            `object`.addProperty("sender", sub.sender)
            `object`.addProperty("source", sub.source)
            `object`.addProperty("parentCode", sub.parentCode)
            val parent = Gson().fromJson(sub.parent, JsonObject::class.java)
            `object`.add("parent", parent)
            Utilities.log("Parent " + sub.parent)
            `object`.add("answers", RealmAnswer.serializeRealmAnswer(sub.answers!!))
            Utilities.log("Parent Exam " + (exam == null))
            if (exam != null && parent == null) `object`.add("parent", RealmStepExam.serializeExam(mRealm, exam))
            if (TextUtils.isEmpty(sub.user)) {
                `object`.add("user", user!!.serialize())
            } else {
                `object`.add("user", JsonParser.parseString(sub.user))
            }
            Utilities.log("SerializeExamResult sub $`object`")
            return `object`
        }

        @JvmStatic
        fun isStepCompleted(realm: Realm, id: String?, userId: String): Boolean {
            val exam = realm.where(RealmStepExam::class.java).equalTo("stepId", id).findFirst() ?: return true
            Utilities.log("Is step completed " + exam.id + " " + userId)
            return exam.id?.let {
                realm.where(RealmSubmission::class.java).equalTo("userId", userId)
                    .contains("parentId", it).notEqualTo("status", "pending").findFirst()
            } != null
        }

        @JvmStatic
        fun createSubmission(sub: RealmSubmission?, mRealm: Realm): RealmSubmission {
            var sub = sub
            if (sub == null || sub.status == "complete" && sub.type == "exam") sub =
                mRealm.createObject(RealmSubmission::class.java, UUID.randomUUID().toString())
            sub!!.lastUpdateTime = Date().time
            return sub
        }

        @JvmStatic
        @Throws(IOException::class)
        fun continueResultUpload(sub: RealmSubmission, apiInterface: ApiInterface, realm: Realm, context: Context?) {
            var `object`: JsonObject? = null
            if (!TextUtils.isEmpty(sub.userId) && sub.userId!!.startsWith("guest")) return
            `object` = if (TextUtils.isEmpty(sub._id)) {
                apiInterface.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/submissions", serializeExamResult(realm, sub, context)).execute().body()
            } else {
                apiInterface.putDoc(Utilities.header, "application/json", Utilities.getUrl() + "/submissions/" + sub._id, serializeExamResult(realm, sub, context)).execute().body()
            }
            if (`object` != null) {
                sub._id = JsonUtils.getString("id", `object`)
                sub._rev = JsonUtils.getString("rev", `object`)
            }
        }

        @JvmStatic
        fun getNoOfSubmissionByUser(id: String?, userId: String?, mRealm: Realm): String {
            val submissionCount = mRealm.where(RealmSubmission::class.java).equalTo("parentId", id).equalTo("userId", userId).findAll().size
            var pluralizedString: String? = null
            if (submissionCount <= 1) {
                pluralizedString = MainApplication.context.getString(R.string.time)
            } else if (submissionCount > 1) {
                pluralizedString = MainApplication.context.getString(R.string.times)
            }
            return MainApplication.context.getString(R.string.survey_taken) + " " + submissionCount + " " + pluralizedString
        }

        @JvmStatic
        fun getNoOfSurveySubmissionByUser(userId: String?, mRealm: Realm): Int {
            return mRealm.where(RealmSubmission::class.java).equalTo("userId", userId)
                .equalTo("type", "survey").equalTo("status", "pending", Case.INSENSITIVE)
                .findAll().size
        }

        @JvmStatic
        fun getRecentSubmissionDate(id: String?, userId: String?, mRealm: Realm): String {
            val s = mRealm.where(RealmSubmission::class.java).equalTo("parentId", id).equalTo("userId", userId).sort("startTime", Sort.DESCENDING).findFirst()
            return if (s == null) "" else TimeUtils.getFormatedDateWithTime(
                s.startTime
            ) + ""
        }

        @JvmStatic
        fun getExamMap(mRealm: Realm, submissions: List<RealmSubmission>): HashMap<String?, RealmStepExam> {
            val exams = HashMap<String?, RealmStepExam>()
            for (sub in submissions) {
                var id = sub.parentId
                if (checkParentId(sub.parentId)) {
                    id = sub.parentId!!.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                }
                val survey = mRealm.where(RealmStepExam::class.java).equalTo("id", id).findFirst()
                if (survey != null) exams[sub.parentId] = survey
            }
            return exams
        }

        private fun checkParentId(parentId: String?): Boolean {
            return parentId != null && parentId.contains("@")
        }
    }
}
