package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmResults
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel

class CourseProgressRepositoryImpl @Inject constructor(
    override val databaseService: DatabaseService
) : CourseProgressRepository {
    override fun getCourseProgress(userId: String?): Map<String, JsonObject> {
        val realm = databaseService.realmInstance
        val progressMap = RealmCourseProgress.getCourseProgress(realm, userId)
        val newProgressMap = HashMap<String, JsonObject>()
        progressMap.forEach { (key, value) ->
            if (key != null) {
                newProgressMap[key] = value
            }
        }
        return newProgressMap
    }

    override fun fetchCourseData(realm: Realm, userId: String?): JsonArray {
        val mycourses = RealmMyCourse.getMyCourseByUserId(
            userId,
            realm.where(RealmMyCourse::class.java).findAll()
        )
        val arr = JsonArray()
        val courseProgress = getCourseProgress(userId)

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
        return arr
    }

    private fun submissionMap(
        submissions: RealmResults<RealmSubmission>,
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

    override fun getCourseProgress(courseData: JsonArray, courseId: String): JsonObject? {
        courseData.forEach { element ->
            val course = element.asJsonObject
            if (course.get("courseId").asString == courseId) {
                return course.getAsJsonObject("progress")
            }
        }
        return null
    }

    override fun countUsersWhoCompletedCourse(realm: Realm, courseId: String): Int {
        var completedCount = 0
        val allUsers = realm.where(RealmUserModel::class.java).findAll()

        allUsers.forEach { user ->
            val userId = user.id
            val courses =
                RealmMyCourse.getMyCourseByUserId(
                    userId,
                    realm.where(RealmMyCourse::class.java).findAll()
                )

            val course = courses.find { it.courseId == courseId }
            if (course != null) {
                val steps = RealmMyCourse.getCourseSteps(realm, courseId)
                val currentProgress =
                    RealmCourseProgress.getCurrentProgress(steps, realm, userId, courseId)

                if (currentProgress == steps.size) {
                    completedCount++
                }
            }
        }
        return completedCount
    }

    override fun getCourseSteps(userId: String?, courseId: String): JsonArray {
        val realm = databaseService.realmInstance
        val steps = realm.where(org.ole.planet.myplanet.model.RealmCourseStep::class.java).contains("courseId", courseId).findAll()
        val array = JsonArray()
        steps.map {
            val ob = JsonObject()
            ob.addProperty("stepId", it.id)
            val exams = realm.where(RealmStepExam::class.java).equalTo("stepId", it.id).findAll()
            getExamObject(userId, exams, ob)
            array.add(ob)
        }
        return array
    }

    private fun getExamObject(userId: String?, exams: RealmResults<RealmStepExam>, ob: JsonObject) {
        val realm = databaseService.realmInstance
        exams.forEach { it ->
            it.id?.let { it1 ->
                realm.where(RealmSubmission::class.java).equalTo("userId", userId)
                    .contains("parentId", it1).equalTo("type", "exam").findAll()
            }?.map {
                val answers = realm.where(RealmAnswer::class.java).equalTo("submissionId", it.id).findAll()
                var examId = it.parentId
                if (it.parentId?.contains("@") == true) {
                    examId = it.parentId!!.split("@")[0]
                }
                val questions = realm.where(RealmExamQuestion::class.java).equalTo("examId", examId).findAll()
                ob.addProperty("completed", questions.size == answers.size)
                ob.addProperty("percentage", (answers.size.div(questions.size)) * 100)
                ob.addProperty("status", it.status)
            }
        }
    }
}
