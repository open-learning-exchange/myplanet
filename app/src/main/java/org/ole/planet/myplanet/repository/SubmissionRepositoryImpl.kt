package org.ole.planet.myplanet.repository

import io.realm.Case
import io.realm.Sort
import java.util.Date
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Case
import io.realm.Sort
import java.util.Date
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.datamanager.ApiClient
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.utilities.UrlUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.model.RealmSubmission.Companion.generateParentId
import java.util.UUID
import io.realm.Realm
import android.text.TextUtils
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmUserModel

class SubmissionRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @ApplicationContext private val context: Context
) : RealmRepository(databaseService), SubmissionRepository {

    private fun RealmSubmission.examIdFromParentId(): String? {
        return parentId?.substringBefore("@")
    }

    override suspend fun getPendingSurveys(userId: String?): List<RealmSubmission> {
        if (userId == null) return emptyList()

        return queryList(RealmSubmission::class.java) {
            equalTo("userId", userId)
            equalTo("status", "pending")
            equalTo("type", "survey")
        }
    }

    override suspend fun getUniquePendingSurveys(userId: String?): List<RealmSubmission> {
        if (userId == null) return emptyList()

        val pendingSurveys = getPendingSurveys(userId)
        if (pendingSurveys.isEmpty()) return emptyList()

        val examIds = pendingSurveys.mapNotNull { it.examIdFromParentId() }.distinct()

        if (examIds.isEmpty()) return emptyList()

        val exams = queryList(RealmStepExam::class.java) {
            `in`("id", examIds.toTypedArray())
        }
        val validExamIds = exams.mapNotNull { it.id }.toSet()

        val uniqueSurveys = linkedMapOf<String, RealmSubmission>()
        pendingSurveys.forEach { submission ->
            val examId = submission.examIdFromParentId()
            if (examId != null && validExamIds.contains(examId) && !uniqueSurveys.containsKey(examId)) {
                uniqueSurveys[examId] = submission
            }
        }

        return uniqueSurveys.values.toList()
    }

    override suspend fun getSurveyTitlesFromSubmissions(
        submissions: List<RealmSubmission>
    ): List<String> {
        val examIds = submissions.mapNotNull { it.examIdFromParentId() }
        if (examIds.isEmpty()) {
            return emptyList()
        }

        val exams = queryList(RealmStepExam::class.java) {
            `in`("id", examIds.toTypedArray())
        }
        val examMap = exams.associate { it.id to (it.name ?: "") }

        return submissions.map { submission ->
            val examId = submission.examIdFromParentId()
            examMap[examId] ?: ""
        }
    }

    override suspend fun getExamMapForSubmissions(
        submissions: List<RealmSubmission>
    ): Map<String?, RealmStepExam> {
        val examIds = submissions.mapNotNull { it.examIdFromParentId() }.distinct()

        if (examIds.isEmpty()) {
            return emptyMap()
        }

        val examMap = queryList(RealmStepExam::class.java) {
            `in`("id", examIds.toTypedArray())
        }.associateBy { it.id }

        return submissions.mapNotNull { sub ->
            val parentId = sub.parentId
            val examId = sub.examIdFromParentId()
            examMap[examId]?.let { parentId to it }
        }.toMap()
    }

    override suspend fun getExamQuestionCount(stepId: String): Int {
        return findByField(RealmStepExam::class.java, "stepId", stepId)?.noOfQuestions ?: 0
    }

    override suspend fun getSubmissionById(id: String): RealmSubmission? {
        return findByField(RealmSubmission::class.java, "id", id)
    }

    override suspend fun getSubmissionsByIds(ids: List<String>): List<RealmSubmission> {
        if (ids.isEmpty()) return emptyList()

        return queryList(RealmSubmission::class.java) {
            `in`("id", ids.toTypedArray())
        }
    }

    override suspend fun getSubmissionsByUserId(userId: String): List<RealmSubmission> {
        return queryList(RealmSubmission::class.java) {
            equalTo("userId", userId)
        }
    }

    override suspend fun hasSubmission(
        stepExamId: String?,
        courseId: String?,
        userId: String?,
        type: String,
    ): Boolean {
        if (stepExamId.isNullOrBlank() || courseId.isNullOrBlank() || userId.isNullOrBlank()) {
            return false
        }

        val questions = queryList(RealmExamQuestion::class.java) {
            equalTo("examId", stepExamId)
        }
        if (questions.isEmpty()) {
            return false
        }

        val examId = questions.firstOrNull()?.examId
        if (examId.isNullOrBlank()) {
            return false
        }

        val parentId = "$examId@$courseId"
        return count(RealmSubmission::class.java) {
            equalTo("userId", userId)
            equalTo("parentId", parentId)
            equalTo("type", type)
        } > 0
    }

    override suspend fun hasPendingOfflineSubmissions(): Boolean {
        return count(RealmSubmission::class.java) {
            beginGroup()
            equalTo("isUpdated", true)
            or()
            isEmpty("_id")
            endGroup()
        } > 0
    }

    override suspend fun hasPendingExamResults(): Boolean {
        return count(RealmSubmission::class.java) {
            equalTo("status", "pending", Case.INSENSITIVE)
            isNotEmpty("answers")
        } > 0
    }

    override suspend fun createSurveySubmission(examId: String, userId: String?) {
        executeTransaction { realm ->
            val courseId = realm.where(RealmStepExam::class.java).equalTo("id", examId).findFirst()?.courseId
            val parentId = if (!courseId.isNullOrEmpty()) {
                examId + "@" + courseId
            } else {
                examId
            }
            var sub = realm.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .equalTo(
                    "parentId",
                    parentId,
                )
                .sort("lastUpdateTime", Sort.DESCENDING)
                .equalTo("status", "pending")
                .findFirst()
import io.realm.Realm

            sub = createSubmission(sub, realm)
            sub.parentId = parentId
            sub.userId = userId
            sub.type = "survey"
            sub.status = "pending"
            sub.startTime = Date().time
        }
    }

    private fun createSubmission(sub: RealmSubmission?, mRealm: Realm): RealmSubmission {
        var submission = sub
        if (submission == null || submission.status == "complete" && (submission.type == "exam" || submission.type == "survey"))
            submission = mRealm.createObject(RealmSubmission::class.java, UUID.randomUUID().toString())
        submission!!.lastUpdateTime = Date().time
        return submission
    }

    override suspend fun saveSubmission(submission: RealmSubmission) {
        try {
            save(submission)
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun getNoOfSurveySubmissionByUser(userId: String?): Int {
        if (userId == null) return 0
        return count(RealmSubmission::class.java) {
            equalTo("userId", userId)
            equalTo("type", "survey")
            equalTo("status", "pending", Case.INSENSITIVE)
        }.toInt()
    }

    override suspend fun getNoOfSubmissionByUser(id: String?, courseId: String?, userId: String?): String {
        if (id == null || userId == null) return "No Submissions Found"
        val submissionParentId = generateParentId(courseId, id)
        if (submissionParentId.isNullOrEmpty()) return "No Submissions Found"
        val submissionCount = count(RealmSubmission::class.java) {
            equalTo("parentId", submissionParentId)
            equalTo("userId", userId)
            `in`("status", arrayOf("complete", "pending"))
        }.toInt()
        return context.resources.getQuantityString(
            R.plurals.survey_taken_count,
            submissionCount,
            submissionCount
        )
    }

    override suspend fun getRecentSubmissionDate(id: String?, courseId: String?, userId: String?): String {
        if (id == null || userId == null) return ""
        val submissionParentId = generateParentId(courseId, id)
        if (submissionParentId.isNullOrEmpty()) return ""
        val recentSubmission = find(RealmSubmission::class.java) {
            equalTo("parentId", submissionParentId)
            equalTo("userId", userId)
            sort("startTime", Sort.DESCENDING)
        }
        return recentSubmission?.startTime?.let { TimeUtils.getFormattedDateWithTime(it) } ?: ""
    }

    override suspend fun getNoOfSubmissionByTeam(teamId: String?, examId: String?): String {
        val submissionCount = count(RealmSubmission::class.java) {
            equalTo("team", teamId)
            equalTo("type", "survey")
            equalTo("parentId", examId)
            equalTo("status", "complete")
        }.toInt()
        return context.resources.getQuantityString(
            R.plurals.survey_taken_count,
            submissionCount,
            submissionCount
        )
    }

    override suspend fun isStepCompleted(id: String?, userId: String?): Boolean {
        val exam = find(RealmStepExam::class.java) {
            equalTo("stepId", id)
        } ?: return true

        return exam.id?.let {
            val submission = find(RealmSubmission::class.java) {
                equalTo("userId", userId)
                contains("parentId", it)
                notEqualTo("status", "pending")
            }
            submission != null
        } ?: false
    }

    override suspend fun insertSubmission(submission: com.google.gson.JsonObject) {
        executeTransaction { mRealm ->
            if (submission.has("_attachments")) {
                return@executeTransaction
            }
            val id = JsonUtils.getString("_id", submission)
            var sub = mRealm.where(RealmSubmission::class.java).equalTo("_id", id).findFirst()
            if (sub == null) {
                sub = mRealm.createObject(RealmSubmission::class.java, id)
            }
            sub?.let {
                it._id = id
                it.status = JsonUtils.getString("status", submission)
                it._rev = JsonUtils.getString("_rev", submission)
                it.grade = JsonUtils.getLong("grade", submission)
                it.type = JsonUtils.getString("type", submission)
                it.uploaded = JsonUtils.getString("status", submission) == "graded"
                it.startTime = JsonUtils.getLong("startTime", submission)
                it.lastUpdateTime = JsonUtils.getLong("lastUpdateTime", submission)
                it.parentId = JsonUtils.getString("parentId", submission)
                it.sender = JsonUtils.getString("sender", submission)
                it.source = JsonUtils.getString("source", submission)
                it.parentCode = JsonUtils.getString("parentCode", submission)
                it.parent = Gson().toJson(JsonUtils.getJsonObject("parent", submission))
                it.user = Gson().toJson(JsonUtils.getJsonObject("user", submission))
                it.team = JsonUtils.getString("team", submission)

                val userJson = JsonUtils.getJsonObject("user", submission)
                if (userJson.has("membershipDoc")) {
                    val membershipJson = JsonUtils.getJsonObject("membershipDoc", userJson)
                    if (membershipJson.entrySet().isNotEmpty()) {
                        val membership = mRealm.createObject(RealmMembershipDoc::class.java)
                        membership.teamId = JsonUtils.getString("teamId", membershipJson)
                        it.membershipDoc = membership
                    }
                }

                val userId = JsonUtils.getString("_id", JsonUtils.getJsonObject("user", submission))
                it.userId = if (userId.contains("@")) {
                    val us = userId.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (us[0].startsWith("org.couchdb.user:")) us[0] else "org.couchdb.user:${us[0]}"
                } else {
                    userId
                }

                if (submission.has("answers")) {
                    val answersArray = submission.get("answers").asJsonArray
                    it.answers = RealmList<RealmAnswer>()

                    for (i in 0 until answersArray.size()) {
                        val answerJson = answersArray[i].asJsonObject
                        val realmAnswer = mRealm.createObject(RealmAnswer::class.java, UUID.randomUUID().toString())

                        realmAnswer.value = JsonUtils.getString("value", answerJson)
                        realmAnswer.mistakes = JsonUtils.getInt("mistakes", answerJson)
                        realmAnswer.isPassed = JsonUtils.getBoolean("passed", answerJson)
                        realmAnswer.submissionId = it._id
                        realmAnswer.examId = it.parentId

                        val examIdPart = it.parentId?.split("@")?.get(0) ?: it.parentId
                        realmAnswer.questionId = if (answerJson.has("questionId")) {
                            JsonUtils.getString("questionId", answerJson)
                        } else {
                            "$examIdPart-$i"
                        }

                        it.answers?.add(realmAnswer)
                    }
                }
            }
        }
    }

    override suspend fun serializeExamResult(sub: RealmSubmission): com.google.gson.JsonObject {
        return withRealm { mRealm ->
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
            val parent = Gson().fromJson(sub.parent, JsonObject::class.java)
            `object`.add("parent", parent)
            `object`.add("answers", RealmAnswer.serializeRealmAnswer(sub.answers ?: io.realm.RealmList()))
            if (exam != null && parent == null) `object`.add("parent", RealmStepExam.serializeExam(mRealm, exam))
            if (TextUtils.isEmpty(sub.user)) {
                `object`.add("user", user?.serialize())
            } else {
                `object`.add("user", JsonParser.parseString(sub.user))
            }
            `object`
        }
    }

    override suspend fun continueResultUpload(sub: RealmSubmission) {
        withRealm { realm ->
            if (!TextUtils.isEmpty(sub.userId) && sub.userId?.startsWith("guest") == true) return@withRealm
            val apiInterface = ApiClient.client.create(ApiInterface::class.java)
            val `object`: JsonObject? = if (TextUtils.isEmpty(sub._id)) {
                apiInterface?.postDoc(UrlUtils.header, "application/json", UrlUtils.getUrl() + "/submissions", serializeExamResult(sub))?.execute()?.body()
            } else {
                apiInterface?.putDoc(UrlUtils.header, "application/json", UrlUtils.getUrl() + "/submissions/" + sub._id, serializeExamResult(sub))?.execute()?.body()
            }
            if (`object` != null) {
                sub._id = JsonUtils.getString("id", `object`)
                sub._rev = JsonUtils.getString("rev", `object`)
            }
        }
    }
}
