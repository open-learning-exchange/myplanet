package org.ole.planet.myplanet.repository

import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.Sort
import io.realm.kotlin.createObject
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCertification
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMembershipDoc
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmTeamReference
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.exam.ExamSubmissionUtils
import org.ole.planet.myplanet.utilities.GsonUtils

class ExamRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : RealmRepository(databaseService), ExamRepository {

    override suspend fun getSubmission(id: String): RealmSubmission? {
        return withRealm { realm ->
            realm.where(RealmSubmission::class.java).equalTo("id", id).findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getExamByStepId(stepId: String): RealmStepExam? {
        return withRealm { realm ->
            realm.where(RealmStepExam::class.java).equalTo("stepId", stepId).findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getExamById(id: String): RealmStepExam? {
        return withRealm { realm ->
            realm.where(RealmStepExam::class.java).equalTo("id", id).findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getCourseProgress(courseId: String, stepNum: Int): org.ole.planet.myplanet.model.RealmCourseProgress? {
        return withRealm { realm ->
            realm.where(org.ole.planet.myplanet.model.RealmCourseProgress::class.java)
                .equalTo("courseId", courseId)
                .equalTo("stepNum", stepNum)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun updateCourseProgress(courseId: String, stepNum: Int, passed: Boolean) {
        executeTransactionAsync { realm ->
            val progress = realm.where(org.ole.planet.myplanet.model.RealmCourseProgress::class.java)
                .equalTo("courseId", courseId)
                .equalTo("stepNum", stepNum)
                .findFirst()
            progress?.passed = passed
        }
    }

    override suspend fun updateSubmissionStatus(submissionId: String, status: String) {
        executeTransactionAsync { realm ->
            val submission = realm.where(RealmSubmission::class.java)
                .equalTo("id", submissionId)
                .findFirst()
            submission?.status = status
        }
    }

    override suspend fun insertPhotoSubmission(
        submissionId: String,
        examId: String?,
        courseId: String?,
        userId: String?,
        date: String,
        uniqueId: String,
        photoPath: String?
    ) {
        executeTransactionAsync { realm ->
            val submit = realm.createObject<RealmSubmitPhotos>(UUID.randomUUID().toString())
            submit.submissionId = submissionId
            submit.examId = examId
            submit.courseId = courseId
            submit.memberId = userId
            submit.date = date
            submit.uniqueId = uniqueId
            submit.photoLocation = photoPath
            submit.uploaded = false
        }
    }

    override suspend fun getExamQuestions(examId: String?): List<RealmExamQuestion> {
        return withRealm { realm ->
            realm.where(RealmExamQuestion::class.java).equalTo("examId", examId).findAll()
                .let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getPendingSubmission(userId: String?, parentId: String, type: String?): RealmSubmission? {
        return withRealm { realm ->
            val query = realm.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .equalTo("parentId", parentId)
                .sort("startTime", Sort.DESCENDING)
            if (type == "exam") {
                query.equalTo("status", "pending")
            }
            query.findFirst()?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun isCourseCertified(courseId: String?): Boolean {
        return withRealm { realm ->
            RealmCertification.isCourseCertified(realm, courseId)
        }
    }

    override suspend fun createSubmission(
        exam: org.ole.planet.myplanet.model.RealmStepExam?,
        user: RealmUserModel?,
        id: String?,
        type: String?,
        isTeam: Boolean,
        teamId: String?
    ): RealmSubmission? {
        return executeTransactionAsyncWithResult { realm ->
            val sub = RealmSubmission.createSubmission(null, realm)
            sub.parentId = when {
                !TextUtils.isEmpty(exam?.id) -> if (!TextUtils.isEmpty(exam?.courseId)) {
                    "${exam?.id}@${exam?.courseId}"
                } else {
                    exam?.id
                }
                !TextUtils.isEmpty(id) -> if (!TextUtils.isEmpty(exam?.courseId)) {
                    "$id@${exam?.courseId}"
                } else {
                    id
                }
                else -> sub.parentId
            }
            try {
                val parentJsonString = JSONObject().apply {
                    put("_id", exam?.id ?: id)
                    put("name", exam?.name ?: "")
                    put("courseId", exam?.courseId ?: "")
                    put("sourcePlanet", exam?.sourcePlanet ?: "")
                    put("teamShareAllowed", exam?.isTeamShareAllowed ?: false)
                    put("noOfQuestions", exam?.noOfQuestions ?: 0)
                    put("isFromNation", exam?.isFromNation ?: false)
                }.toString()
                sub.parent = parentJsonString
            } catch (e: Exception) {
                e.printStackTrace()
            }
            sub.userId = user?.id
            sub.status = "pending"
            sub.type = type
            sub.startTime = Date().time
            sub.lastUpdateTime = Date().time
            if (isTeam && teamId != null) {
                val team = realm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
                if (team != null) {
                    val teamRef = realm.createObject(RealmTeamReference::class.java)
                    teamRef._id = team._id
                    teamRef.name = team.name
                    teamRef.type = team.type ?: "team"
                    sub.teamObject = teamRef
                }
                val membershipDoc = realm.createObject(RealmMembershipDoc::class.java)
                membershipDoc.teamId = teamId
                sub.membershipDoc = membershipDoc
                try {
                    val userJson = JSONObject()
                    userJson.put("age", user?.dob ?: "")
                    userJson.put("gender", user?.gender ?: "")
                    val membershipJson = JSONObject()
                    membershipJson.put("teamId", teamId)
                    userJson.put("membershipDoc", membershipJson)
                    sub.user = userJson.toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            realm.copyFromRealm(sub)
        }
    }

    override suspend fun clearAllExistingAnswers(
        examId: String?,
        courseId: String?,
        userId: String?,
        id: String?
    ) {
        executeTransactionAsync { realm ->
            val parentIdToSearch = if (!TextUtils.isEmpty(courseId)) {
                "${examId ?: id}@${courseId}"
            } else {
                examId ?: id
            }
            val allSubmissions = realm.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .equalTo("parentId", parentIdToSearch)
                .findAll()
            allSubmissions.forEach { submission ->
                submission.answers?.deleteAllFromRealm()
                submission.deleteFromRealm()
            }
        }
    }

    override suspend fun saveAnswer(
        submissionId: String?,
        question: RealmExamQuestion?,
        ans: String,
        listAns: HashMap<String, String>?,
        otherText: String?,
        isEtVisible: Boolean,
        type: String?,
        questionsSize: Int
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var result = false
            databaseService.executeTransactionAsync { realm ->
                val submission = realm.where(RealmSubmission::class.java)
                    .equalTo("id", submissionId)
                    .findFirst()
                result = ExamSubmissionUtils.saveAnswer(
                    realm,
                    submission,
                    question,
                    ans,
                    listAns,
                    otherText,
                    isEtVisible,
                    type ?: "exam",
                    submission?.answers?.size ?: 0,
                    questionsSize
                )
            }
            result
        }
    }
}
