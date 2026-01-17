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
import org.ole.planet.myplanet.model.RealmUserChallengeActions
import org.ole.planet.myplanet.utilities.JsonUtils

class ProgressRepositoryImpl @Inject constructor(databaseService: DatabaseService) : RealmRepository(databaseService), ProgressRepository {
    override suspend fun getCourseProgress(userId: String?): HashMap<String?, JsonObject> {
        val mycourses = queryList(RealmMyCourse::class.java) {
            equalTo("userId", userId)
        }
        val map = HashMap<String?, JsonObject>()
        
        // Batch query all course steps for all courses at once
        val courseIds = mycourses.mapNotNull { it.courseId }
        if (courseIds.isEmpty()) {
            return map
        }
        
        val allSteps = queryList(RealmCourseStep::class.java) {
            `in`("courseId", courseIds.toTypedArray())
        }
        
        // Group steps by courseId for O(1) lookup
        val stepsByCourseId = allSteps.groupBy { it.courseId }
        
        for (course in mycourses) {
            course.courseId?.let { courseId ->
                val progressObject = JsonObject()
                val steps = stepsByCourseId[courseId] ?: emptyList()
                progressObject.addProperty("max", steps.size)
                progressObject.addProperty("current", getCurrentProgress(steps, userId, courseId))
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

    override suspend fun getCurrentProgress(
        steps: List<RealmCourseStep?>?, userId: String?, courseId: String?
    ): Int {
        val progresses = queryList(RealmCourseProgress::class.java) {
            equalTo("userId", userId)
            equalTo("courseId", courseId)
        }
        val completedSteps = progresses.map { it.stepNum }.toSet()
        var i = 0
        while (i < (steps?.size ?: 0)) {
            if (!completedSteps.contains(i + 1)) {
                break
            }
            i++
        }
        return i
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
                "current", getCurrentProgress(steps, userId, course.courseId)
            )
            map[course.courseId] = progressObject
        }
        return map
    }

    @Deprecated("Use getCurrentProgress instead")
    private suspend fun suspendGetCurrentProgress(
        steps: List<RealmCourseStep?>?, userId: String?, courseId: String?
    ): Int {
        return getCurrentProgress(steps, userId, courseId)
    }

    private suspend fun submissionMap(
        submissions: List<RealmSubmission>, examIds: List<String>, obj: JsonObject
    ) {
        var totalMistakes = 0
        val mistakesMap = HashMap<String, Int>()
        
        // Collect all submission IDs to batch query answers
        val submissionIds = submissions.mapNotNull { it.id }
        if (submissionIds.isEmpty()) {
            obj.add("stepMistake", JsonUtils.gson.toJsonTree(mistakesMap).asJsonObject)
            obj.addProperty("mistakes", totalMistakes)
            return
        }
        
        // Batch query all answers for all submissions at once
        val allAnswers = queryList(RealmAnswer::class.java) {
            `in`("submissionId", submissionIds.toTypedArray())
        }
        
        // Collect all question IDs to batch query questions
        val questionIds = allAnswers.mapNotNull { it.questionId }.distinct()
        if (questionIds.isEmpty()) {
            obj.add("stepMistake", JsonUtils.gson.toJsonTree(mistakesMap).asJsonObject)
            obj.addProperty("mistakes", totalMistakes)
            return
        }
        
        // Batch query all questions at once
        val allQuestions = queryList(RealmExamQuestion::class.java) {
            `in`("id", questionIds.toTypedArray())
        }
        
        // Create lookup map for O(1) access
        val questionMap = allQuestions.associateBy { it.id }
        
        // Process answers with cached question data
        allAnswers.forEach { answer ->
            answer.questionId?.let { questionId ->
                val question = questionMap[questionId]
                if (question != null && examIds.contains(question.examId)) {
                    totalMistakes += answer.mistakes
                    val examIndexKey = examIds.indexOf(question.examId).toString()
                    mistakesMap[examIndexKey] = (mistakesMap[examIndexKey] ?: 0) + answer.mistakes
                }
            }
        }
        
        obj.add("stepMistake", JsonUtils.gson.toJsonTree(mistakesMap).asJsonObject)
        obj.addProperty("mistakes", totalMistakes)
    }

    override suspend fun getProgressRecords(userId: String?): List<RealmCourseProgress> {
        return queryList(RealmCourseProgress::class.java) {
            equalTo("userId", userId)
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

    override suspend fun hasUserCompletedSync(userId: String): Boolean {
        return count(RealmUserChallengeActions::class.java) {
            equalTo("userId", userId)
            equalTo("actionType", "sync")
        } > 0
    }
}
