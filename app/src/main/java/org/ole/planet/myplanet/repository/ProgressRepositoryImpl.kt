package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import javax.inject.Inject
import org.json.JSONArray
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.ChallengeCounts
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserChallengeActions
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProgressRepositoryImpl @Inject constructor(private val databaseService: DatabaseService) : ProgressRepository {
    override suspend fun fetchCourseData(userId: String?): JsonArray {
        return databaseService.withRealmAsync { realm ->
            val mycourses = RealmMyCourse.getMyCourseByUserId(
                userId,
                realm.where(RealmMyCourse::class.java).findAll()
            )
            val arr = JsonArray()
            val courseProgress = RealmCourseProgress.getCourseProgress(realm, userId)
            mycourses.forEach { course ->
                val obj = JsonObject()
                obj.addProperty("courseName", course.courseTitle)
                obj.addProperty("courseId", course.courseId)
                obj.add("progress", courseProgress[course.id])
                val submissions = course.courseId?.let { courseId ->
                    realm.where(RealmSubmission::class.java)
                        .equalTo("userId", userId)
                        .contains("parentId", courseId)
                        .equalTo("type", "exam")
                        .findAll()
                }
                val exams = realm.where(RealmStepExam::class.java)
                    .equalTo("courseId", course.courseId)
                    .findAll()
                val examIds: List<String> = exams.map { it.id as String }
                if (submissions != null) {
                    submissionMap(submissions, realm, examIds, obj)
                }
                arr.add(obj)
            }
            arr
        }
    }

    private fun submissionMap(
        submissions: List<RealmSubmission>,
        realm: Realm,
        examIds: List<String>,
        obj: JsonObject
    ) {
        var totalMistakes = 0
        submissions.forEach {
            val answers = realm.where(RealmAnswer::class.java)
                .equalTo("submissionId", it.id)
                .findAll()
            val mistakesMap = HashMap<String, Int>()
            answers.forEach { r ->
                val question = realm.where(RealmExamQuestion::class.java)
                    .equalTo("id", r.questionId)
                    .findFirst()
                if (examIds.contains(question?.examId)) {
                    totalMistakes += r.mistakes
                    if (mistakesMap.containsKey(question?.examId)) {
                        mistakesMap["${examIds.indexOf(question?.examId)}"] =
                            mistakesMap[question?.examId]!!.plus(r.mistakes)
                    } else {
                        mistakesMap["${examIds.indexOf(question?.examId)}"] = r.mistakes
                    }
                }
            }
            obj.add(
                "stepMistake",
                Gson().fromJson(Gson().toJson(mistakesMap), JsonObject::class.java)
            )
            obj.addProperty("mistakes", totalMistakes)
        }
    }

    override suspend fun getChallengeCounts(
        userId: String?,
        startTime: Long,
        endTime: Long,
        courseId: String
    ): ChallengeCounts {
        return databaseService.withRealmAsync { realm ->
            val uniqueDates = fetchVoiceDates(realm, startTime, endTime, userId)
            val allUniqueDates = fetchVoiceDates(realm, startTime, endTime, null)
            val courseName = realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", courseId)
                .findFirst()?.courseTitle
            val hasUnfinishedSurvey = hasPendingSurvey(realm, courseId)
            val hasSyncAction = realm.where(RealmUserChallengeActions::class.java)
                .equalTo("userId", userId)
                .equalTo("actionType", "sync")
                .count() > 0

            ChallengeCounts(
                voiceCount = uniqueDates.size,
                allVoiceCount = allUniqueDates.size,
                hasUnfinishedSurvey = hasUnfinishedSurvey,
                courseName = courseName,
                hasSyncAction = hasSyncAction
            )
        }
    }

    private fun fetchVoiceDates(realm: Realm, start: Long, end: Long, userId: String?): List<String> {
        val query = realm.where(RealmNews::class.java)
            .greaterThanOrEqualTo("time", start)
            .lessThanOrEqualTo("time", end)
        if (userId != null) query.equalTo("userId", userId)
        val results = query.findAll()
        return results.filter { isCommunitySection(it) }
            .map { getDateFromTimestamp(it.time) }
            .distinct()
    }

    private fun isCommunitySection(news: RealmNews): Boolean {
        news.viewIn?.let { viewInStr ->
            try {
                val viewInArray = JSONArray(viewInStr)
                for (i in 0 until viewInArray.length()) {
                    val viewInObj = viewInArray.getJSONObject(i)
                    if (viewInObj.optString("section") == "community") {
                        return true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    private fun getDateFromTimestamp(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    private fun hasPendingSurvey(realm: Realm, courseId: String): Boolean {
        return realm.where(RealmStepExam::class.java)
            .equalTo("courseId", courseId)
            .equalTo("type", "survey")
            .findAll()
            .any { survey -> !TakeCourseFragment.existsSubmission(realm, survey.id, "survey") }
    }
}
