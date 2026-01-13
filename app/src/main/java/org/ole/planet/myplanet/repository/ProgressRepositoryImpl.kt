package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.utilities.JsonUtils

class ProgressRepositoryImpl @Inject constructor(databaseService: DatabaseService) : RealmRepository(databaseService), ProgressRepository {
    override suspend fun getCourseProgress(userId: String?): HashMap<String?, JsonObject> {
        val mycourses = queryList(RealmMyCourse::class.java) {
            equalTo("userId", userId)
        }
        val map = HashMap<String?, JsonObject>()
        for (course in mycourses) {
            course.courseId?.let { courseId ->
                val progressObject = JsonObject()
                val steps = queryList(RealmCourseStep::class.java) {
                    equalTo("courseId", courseId)
                }
                progressObject.addProperty("max", steps.size)
                val progresses = queryList(RealmCourseProgress::class.java) {
                    equalTo("userId", userId)
                    equalTo("courseId", courseId)
                }
                val completedSteps = progresses.map { it.stepNum }.toSet()
                var currentProgress = 0
                while (currentProgress < steps.size) {
                    if (!completedSteps.contains(currentProgress + 1)) {
                        break
                    }
                    currentProgress++
                }
                progressObject.addProperty("current", currentProgress)
                map[courseId] = progressObject
            }
        }
        return map
    }

    override suspend fun fetchCourseData(userId: String?): JsonArray {
        val mycourses = queryList(RealmMyCourse::class.java) {
            equalTo("userId", userId)
        }
        val arr = JsonArray()
        val courseProgress = getCourseProgressMap(userId, mycourses)
        mycourses.forEach { course ->
            val obj = JsonObject()
            obj.addProperty("courseName", course.courseTitle)
            obj.addProperty("courseId", course.courseId)
            obj.add("progress", courseProgress[course.courseId])
            val submissions = course.courseId?.let { courseId ->
                queryList(RealmSubmission::class.java) {
                    equalTo("userId", userId)
                    contains("parentId", courseId)
                    equalTo("type", "exam")
                }
            }
            val exams = queryList(RealmStepExam::class.java) {
                equalTo("courseId", course.courseId)
            }
            val examIds: List<String> = exams.mapNotNull { it.id }
            if (!submissions.isNullOrEmpty()) {
                submissionMap(submissions, examIds, obj)
            }
            arr.add(obj)
        }
        return arr
    }

    private suspend fun getCourseProgressMap(
        userId: String?, mycourses: List<RealmMyCourse>
    ): HashMap<String?, JsonObject> {
        val map = HashMap<String?, JsonObject>()
        for (course in mycourses) {
            val progressObject = JsonObject()
            val steps = course.courseSteps ?: emptyList()
            progressObject.addProperty("max", steps.size)
            progressObject.addProperty(
                "current", suspendGetCurrentProgress(steps, userId, course.courseId)
            )
            map[course.courseId] = progressObject
        }
        return map
    }

    private suspend fun suspendGetCurrentProgress(
        steps: List<RealmCourseStep?>?, userId: String?, courseId: String?
    ): Int {
        var i = 0
        while (i < (steps?.size ?: 0)) {
            val progress = queryList(RealmCourseProgress::class.java) {
                equalTo("stepNum", i + 1)
                equalTo("userId", userId)
                equalTo("courseId", courseId)
            }
            if (progress.isEmpty()) {
                break
            }
            i++
        }
        return i
    }

    private suspend fun submissionMap(
        submissions: List<RealmSubmission>, examIds: List<String>, obj: JsonObject
    ) {
        var totalMistakes = 0
        submissions.forEach {
            val answers = queryList(RealmAnswer::class.java) {
                equalTo("submissionId", it.id)
            }
            val mistakesMap = HashMap<String, Int>()
            answers.forEach { r ->
                r.questionId?.let { questionId ->
                    val question = findByField(RealmExamQuestion::class.java, "id", questionId)
                    if (question != null && examIds.contains(question.examId)) {
                        totalMistakes += r.mistakes
                        val examIndexKey = examIds.indexOf(question.examId).toString()
                        mistakesMap[examIndexKey] = (mistakesMap[examIndexKey] ?: 0) + r.mistakes
                    }
                }
            }
            obj.add("stepMistake", JsonUtils.gson.toJsonTree(mistakesMap).asJsonObject)
            obj.addProperty("mistakes", totalMistakes)
        }
    }
    override suspend fun saveCourseProgress(
        userId: String?,
        planetCode: String?,
        parentCode: String?,
        courseId: String?,
        stepNum: Int,
        passed: Boolean?
    ) {
        executeTransaction { realm ->
            var courseProgress = realm.where(RealmCourseProgress::class.java)
                .equalTo("courseId", courseId)
                .equalTo("userId", userId)
                .equalTo("stepNum", stepNum)
                .findFirst()
            if (courseProgress == null) {
                courseProgress =
                    realm.createObject(RealmCourseProgress::class.java, UUID.randomUUID().toString())
                courseProgress.createdDate = Date().time
            }
            courseProgress?.courseId = courseId
            courseProgress?.stepNum = stepNum
            if (passed != null) {
                courseProgress?.passed = passed
            }
            courseProgress?.createdOn = planetCode
            courseProgress?.updatedDate = Date().time
            courseProgress?.parentCode = parentCode
            courseProgress?.userId = userId
        }
    }
}
