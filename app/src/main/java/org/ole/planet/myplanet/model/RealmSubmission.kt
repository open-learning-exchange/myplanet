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
import java.io.IOException
import java.util.Date
import java.util.UUID
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.UrlUtils

open class RealmSubmission : RealmObject() {
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
    var answers: RealmList<RealmAnswer>? = null
    var team: String? = null
    var grade: Long = 0
    var status: String? = null
    var uploaded = false
    var sender: String? = null
    var source: String? = null
    var parentCode: String? = null
    var parent: String? = null
    var membershipDoc: RealmMembershipDoc? = null
    var isUpdated = false

    companion object {
        @JvmStatic
        fun insert(mRealm: Realm, submission: JsonObject) {
            if (submission.has("_attachments")) {
                return
            }

            var transactionStarted = false

            try {
                if (!mRealm.isInTransaction) {
                    mRealm.beginTransaction()
                    transactionStarted = true
                }

                val id = JsonUtils.getString("_id", submission)
                var sub = mRealm.where(RealmSubmission::class.java).equalTo("_id", id).findFirst()
                if (sub == null) {
                    sub = mRealm.createObject(RealmSubmission::class.java, id)
                }
                sub?._id = id
                sub?.status = JsonUtils.getString("status", submission)
                sub?._rev = JsonUtils.getString("_rev", submission)
                sub?.grade = JsonUtils.getLong("grade", submission)
                sub?.type = JsonUtils.getString("type", submission)
                sub?.uploaded = JsonUtils.getString("status", submission) == "graded"
                sub?.startTime = JsonUtils.getLong("startTime", submission)
                sub?.lastUpdateTime = JsonUtils.getLong("lastUpdateTime", submission)
                sub?.parentId = JsonUtils.getString("parentId", submission)
                sub?.sender = JsonUtils.getString("sender", submission)
                sub?.source = JsonUtils.getString("source", submission)
                sub?.parentCode = JsonUtils.getString("parentCode", submission)
                sub?.parent = Gson().toJson(JsonUtils.getJsonObject("parent", submission))
                sub?.user = Gson().toJson(JsonUtils.getJsonObject("user", submission))
                sub.team = JsonUtils.getString("team", submission)

                val userJson = JsonUtils.getJsonObject("user", submission)
                if (userJson.has("membershipDoc")) {
                    val membershipJson = JsonUtils.getJsonObject("membershipDoc", userJson)
                    if (membershipJson.entrySet().isNotEmpty()) {
                        val membership = mRealm.createObject(RealmMembershipDoc::class.java)
                        membership.teamId = JsonUtils.getString("teamId", membershipJson)
                        sub?.membershipDoc = membership
                    }
                }

                val userId = JsonUtils.getString("_id", JsonUtils.getJsonObject("user", submission))
                sub?.userId = if (userId.contains("@")) {
                    val us = userId.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (us[0].startsWith("org.couchdb.user:")) us[0] else "org.couchdb.user:${us[0]}"
                } else {
                    userId
                }

                if (submission.has("answers")) {
                    val answersArray = submission.get("answers").asJsonArray
                    sub?.answers = RealmList<RealmAnswer>()

                    for (i in 0 until answersArray.size()) {
                        val answerJson = answersArray[i].asJsonObject
                        val realmAnswer = mRealm.createObject(RealmAnswer::class.java, UUID.randomUUID().toString())

                        realmAnswer.value = JsonUtils.getString("value", answerJson)
                        realmAnswer.mistakes = JsonUtils.getInt("mistakes", answerJson)
                        realmAnswer.isPassed = JsonUtils.getBoolean("passed", answerJson)
                        realmAnswer.submissionId = sub?._id
                        realmAnswer.examId = sub?.parentId

                        val examIdPart = sub?.parentId?.split("@")?.get(0) ?: sub?.parentId
                        realmAnswer.questionId = if (answerJson.has("questionId")) {
                            JsonUtils.getString("questionId", answerJson)
                        } else {
                            "$examIdPart-$i"
                        }

                        sub?.answers?.add(realmAnswer)
                    }
                }

                if (transactionStarted) {
                    mRealm.commitTransaction()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (transactionStarted && mRealm.isInTransaction) {
                    mRealm.cancelTransaction()
                }
            }
        }

        @JvmStatic
        fun serializeExamResult(mRealm: Realm, sub: RealmSubmission, context: Context): JsonObject {
            val `object` = JsonObject()
            val user = mRealm.where(RealmUserModel::class.java).equalTo("id", sub.userId).findFirst()
            var examId = sub.parentId
            if (sub.parentId?.contains("@") == true) {
                examId = sub.parentId!!.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
            }
            val exam = mRealm.where(RealmStepExam::class.java).equalTo("id", examId).findFirst()
            if (!TextUtils.isEmpty(sub._id)) {
                `object`.addProperty("_id", sub._id)
            }
            if (!TextUtils.isEmpty(sub._rev)) {
                `object`.addProperty("_rev", sub._rev)
            }
            `object`.addProperty("parentId", sub.parentId)
            `object`.addProperty("type", sub.type)
            `object`.addProperty("team", sub.team)
            `object`.addProperty("grade", sub.grade)
            `object`.addProperty("startTime", sub.startTime)
            `object`.addProperty("lastUpdateTime", sub.lastUpdateTime)
            `object`.addProperty("status", sub.status)
            `object`.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            `object`.addProperty("deviceName", NetworkUtils.getDeviceName())
            `object`.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
            `object`.addProperty("sender", sub.sender)
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            `object`.addProperty("source", prefs.getString("planetCode", ""))
            `object`.addProperty("parentCode", prefs.getString("parentCode", ""))
            `object`.add("answers", RealmAnswer.serializeRealmAnswer(sub.answers ?: RealmList()))
            if (exam != null) {
                `object`.add("parent", RealmStepExam.serializeExam(mRealm, exam))
            } else {
                val parent = Gson().fromJson(sub.parent, JsonObject::class.java)
                `object`.add("parent", parent)
            }
            if (TextUtils.isEmpty(sub.user)) {
                `object`.add("user", user?.serialize())
            } else {
                `object`.add("user", JsonParser.parseString(sub.user))
            }
            return `object`
        }

        @JvmStatic
        fun isStepCompleted(realm: Realm, id: String?, userId: String?): Boolean {
            val exam = realm.where(RealmStepExam::class.java).equalTo("stepId", id).findFirst() ?: return true
            return exam.id?.let {
                realm.where(RealmSubmission::class.java).equalTo("userId", userId)
                    .contains("parentId", it).notEqualTo("status", "pending").findFirst()
            } != null
        }

        @JvmStatic
        fun createSubmission(sub: RealmSubmission?, mRealm: Realm): RealmSubmission {
            var submission = sub
            if (submission == null || submission.status == "complete" && (submission.type == "exam" || submission.type == "survey"))
                submission = mRealm.createObject(RealmSubmission::class.java, UUID.randomUUID().toString())
            submission!!.lastUpdateTime = Date().time
            return submission
        }

        @JvmStatic
        @Throws(IOException::class)
        fun continueResultUpload(sub: RealmSubmission, apiInterface: ApiInterface?, realm: Realm, context: Context) {
            if (!TextUtils.isEmpty(sub.userId) && sub.userId?.startsWith("guest") == true) return
            val `object`: JsonObject? = if (TextUtils.isEmpty(sub._id)) {
                apiInterface?.postDoc(UrlUtils.header, "application/json", UrlUtils.getUrl() + "/submissions", serializeExamResult(realm, sub, context))?.execute()?.body()
            } else {
                apiInterface?.putDoc(UrlUtils.header, "application/json", UrlUtils.getUrl() + "/submissions/" + sub._id, serializeExamResult(realm, sub, context))?.execute()?.body()
            }
            if (`object` != null) {
                sub._id = JsonUtils.getString("id", `object`)
                sub._rev = JsonUtils.getString("rev", `object`)
            }
        }

        fun generateParentId(courseId: String?, examId: String?): String? {
            return if (!examId.isNullOrEmpty()) {
                if (!courseId.isNullOrEmpty()) {
                    "$examId@$courseId"
                } else {
                    examId
                }
            } else {
                null
            }
        }

        @JvmStatic
        fun getNoOfSubmissionByUser(id: String?, courseId: String?, userId: String?, mRealm: Realm): String {
            if (id == null || userId == null) return "No Submissions Found"

            val submissionParentId = generateParentId(courseId, id)
            if (submissionParentId.isNullOrEmpty()) return "No Submissions Found"

            val submissionCount = mRealm.where(RealmSubmission::class.java)
                .equalTo("parentId", submissionParentId)
                .equalTo("userId", userId)
                .`in`("status", arrayOf("complete", "pending"))
                .count().toInt()

            return context.resources.getQuantityString(R.plurals.survey_taken_count, submissionCount, submissionCount)
        }

        @JvmStatic
        fun getNoOfSubmissionByTeam(teamId: String?, examId: String?, mRealm: Realm): String {
            val submissionCount = mRealm.where(RealmSubmission::class.java)
                .equalTo("team", teamId)
                .equalTo("type", "survey")
                .equalTo("parentId", examId)
                .equalTo("status", "complete")
                .count().toInt()

            return context.resources.getQuantityString(R.plurals.survey_taken_count, submissionCount, submissionCount)
        }

        @JvmStatic
        fun getNoOfSurveySubmissionByUser(userId: String?, mRealm: Realm): Int {
            if (userId == null) return 0

            return mRealm.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .equalTo("type", "survey")
                .equalTo("status", "pending", Case.INSENSITIVE)
                .count().toInt()
        }

        @JvmStatic
        fun getRecentSubmissionDate(id: String?, courseId:String?, userId: String?, mRealm: Realm): String {
            if (id == null || userId == null) return ""
            val submissionParentId= generateParentId(courseId, id)
            if(submissionParentId.isNullOrEmpty())  return ""
            val recentSubmission = mRealm.where(RealmSubmission::class.java)
                .equalTo("parentId", submissionParentId)
                .equalTo("userId", userId)
                .sort("startTime", Sort.DESCENDING)
                .findFirst()
            return recentSubmission?.startTime?.let { TimeUtils.getFormattedDateWithTime(it) } ?: ""
        }

        @JvmStatic
        fun getExamMap(mRealm: Realm, submissions: List<RealmSubmission>?): HashMap<String?, RealmStepExam> {
            val exams = HashMap<String?, RealmStepExam>()
            for (sub in submissions ?: emptyList()){
                var id = sub.parentId
                if (checkParentId(sub.parentId)) {
                    id = sub.parentId!!.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                }
                val survey = mRealm.where(RealmStepExam::class.java).equalTo("id", id).findFirst()
                if (survey != null) {
                    exams[sub.parentId] = survey
                }
            }
            return exams
        }

        private fun checkParentId(parentId: String?): Boolean {
            return parentId != null && parentId.contains("@")
        }

        @JvmStatic
        fun serialize(mRealm: Realm, submission: RealmSubmission): JsonObject {
            val jsonObject = JsonObject()

            try {
                if (!submission._id.isNullOrEmpty()) {
                    jsonObject.addProperty("_id", submission._id)
                }
                if (!submission._rev.isNullOrEmpty()) {
                    jsonObject.addProperty("_rev", submission._rev)
                }

                jsonObject.addProperty("parentId", submission.parentId ?: "")
                jsonObject.addProperty("type", submission.type ?: "survey")
                jsonObject.addProperty("userId", submission.userId ?: "")
                jsonObject.addProperty("status", submission.status ?: "pending")
                jsonObject.addProperty("team", submission.team ?: "")
                jsonObject.addProperty("uploaded", submission.uploaded)
                jsonObject.addProperty("sender", submission.sender ?: "")
                jsonObject.addProperty("source", submission.source ?: "")
                jsonObject.addProperty("parentCode", submission.parentCode ?: "")
                jsonObject.addProperty("startTime", submission.startTime)
                jsonObject.addProperty("lastUpdateTime", submission.lastUpdateTime)
                jsonObject.addProperty("grade", submission.grade)

                if (!submission.parent.isNullOrEmpty()) {
                    jsonObject.add("parent", JsonParser.parseString(submission.parent))
                }

                if (!submission.user.isNullOrEmpty()) {
                    val userJson = JsonParser.parseString(submission.user).asJsonObject
                    if (submission.membershipDoc != null) {
                        val membershipJson = JsonObject()
                        membershipJson.addProperty("teamId", submission.membershipDoc?.teamId ?: "")

                        userJson.add("membershipDoc", membershipJson)
                    }
                    jsonObject.add("user", userJson)
                }

                val questions = mRealm.where(RealmExamQuestion::class.java)
                    .equalTo("examId", submission.parentId)
                    .findAll()
                val serializedQuestions = RealmExamQuestion.serializeQuestions(questions)
                jsonObject.add("questions", serializedQuestions)

                val answersArray = RealmAnswer.serializeRealmAnswer(submission.answers ?: RealmList())
                jsonObject.add("answers", answersArray)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return jsonObject
        }
    }
}

open class RealmMembershipDoc : RealmObject() {
    var teamId: String? = null
}
