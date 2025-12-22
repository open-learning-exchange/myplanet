package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.utilities.GsonUtils

class ProgressRepositoryImpl @Inject constructor(databaseService: DatabaseService) : RealmRepository(databaseService), ProgressRepository {
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
            obj.add("stepMistake", GsonUtils.gson.toJsonTree(mistakesMap).asJsonObject)
            obj.addProperty("mistakes", totalMistakes)
        }
    }
}
