package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.DatabaseService
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserChallengeActions
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils

class ProgressRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val dispatcherProvider: DispatcherProvider
) : RealmRepository(databaseService, realmDispatcher), ProgressRepository {
    override suspend fun getCourseProgress(userId: String?): HashMap<String?, JsonObject> = withContext(dispatcherProvider.io) {
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
                progressObject.addProperty("current", getCurrentProgress(steps, userId, courseId))
                map[courseId] = progressObject
            }
        }
        map
    }

    override suspend fun fetchCourseData(userId: String?): JsonArray = withContext(dispatcherProvider.io) {
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
        arr
    }

    override suspend fun getCurrentProgress(
        steps: List<RealmCourseStep?>?, userId: String?, courseId: String?
    ): Int = withContext(dispatcherProvider.io) {
        val progresses = queryList(RealmCourseProgress::class.java) {
            equalTo("userId", userId)
            equalTo("courseId", courseId)
        }
        val stepsSize = steps?.size ?: 0
        val completed = BooleanArray(stepsSize + 1)
        progresses.forEach { progress ->
            val stepNum = progress.stepNum
            if (stepNum in 1..stepsSize) {
                completed[stepNum] = true
            }
        }

        var i = 1
        // Loop looks for the first missing step from 1 to stepsSize.
        // It returns the number of consecutive completed steps from the start.
        while (i <= stepsSize && completed[i]) {
            i++
        }
        i - 1
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

    override suspend fun getProgressRecords(userId: String?): List<RealmCourseProgress> = withContext(dispatcherProvider.io) {
        queryList(RealmCourseProgress::class.java) {
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

    override suspend fun hasUserCompletedSync(userId: String): Boolean = withContext(dispatcherProvider.io) {
        count(RealmUserChallengeActions::class.java) {
            equalTo("userId", userId)
            equalTo("actionType", "sync")
        } > 0
    }
}
