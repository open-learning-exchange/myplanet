package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.CourseCompletion
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils

class ProgressRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val dispatcherProvider: DispatcherProvider,
    private val coursesRepositoryLazy: dagger.Lazy<CoursesRepository>,
    private val activitiesRepositoryLazy: dagger.Lazy<ActivitiesRepository>
) : RealmRepository(databaseService, realmDispatcher), ProgressRepository {
    override suspend fun getCourseProgress(courseIds: List<String>, userId: String?): HashMap<String?, JsonObject> {
        val courseIdsArray = courseIds.toTypedArray()
        val allSteps = if (courseIdsArray.isEmpty()) emptyList() else queryList(RealmCourseStep::class.java) {
            `in`("courseId", courseIdsArray)
        }
        val allProgresses = if (courseIdsArray.isEmpty()) emptyList() else queryList(RealmCourseProgress::class.java) {
            equalTo("userId", userId)
            `in`("courseId", courseIdsArray)
        }

        val stepsByCourseId = allSteps.groupBy { it.courseId }
        val progressesByCourseId = allProgresses.groupBy { it.courseId }

        val map = HashMap<String?, JsonObject>()
        for (courseId in courseIds) {
            val progressObject = JsonObject()
            val steps = stepsByCourseId[courseId] ?: emptyList()
            val progresses = progressesByCourseId[courseId] ?: emptyList()
            progressObject.addProperty("max", steps.size)
            progressObject.addProperty("current", calculateCurrentProgress(steps, progresses))
            map[courseId] = progressObject
        }
        return map
    }

    override suspend fun fetchCourseData(userId: String?): JsonArray {
        val mycourses = coursesRepositoryLazy.get().getMyCourses(userId ?: "")
        val arr = JsonArray()
        val courseIds = mycourses.mapNotNull { it.courseId }
        val courseProgress = getCourseProgress(courseIds, userId)
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
        return calculateCurrentProgress(steps, progresses)
    }

    private fun calculateCurrentProgress(
        steps: List<RealmCourseStep?>?, progresses: List<RealmCourseProgress>
    ): Int {
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
        return i - 1
    }

    private suspend fun submissionMap(
        submissions: List<RealmSubmission>, examIds: List<String>, obj: JsonObject
    ) {
        val submissionIds = submissions.mapNotNull { it.id }.toTypedArray()
        val allAnswers = if (submissionIds.isEmpty()) emptyList() else queryList(RealmAnswer::class.java) {
            `in`("submissionId", submissionIds)
        }

        val questionIds = allAnswers.mapNotNull { it.questionId }.distinct().toTypedArray()
        val allQuestions = if (questionIds.isEmpty()) emptyList() else queryList(RealmExamQuestion::class.java) {
            `in`("id", questionIds)
        }
        val questionsMap = allQuestions.associateBy { it.id }

        val answersBySubmissionId = allAnswers.groupBy { it.submissionId }

        var totalMistakes = 0
        submissions.forEach { submission ->
            val answers = answersBySubmissionId[submission.id] ?: emptyList()
            val mistakesMap = HashMap<String, Int>()
            answers.forEach { r ->
                r.questionId?.let { questionId ->
                    val question = questionsMap[questionId]
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

    override suspend fun getProgressRecords(userId: String?): List<RealmCourseProgress> {
        return queryList(RealmCourseProgress::class.java) {
            equalTo("userId", userId)
        }
    }

    override suspend fun getCompletedCourses(userId: String): List<CourseCompletion> {
        val myCourses = coursesRepositoryLazy.get().getMyCourses(userId)
        val allProgressRecords = getProgressRecords(userId)

        val completedCourses = mutableListOf<CourseCompletion>()
        myCourses.forEachIndexed { index, course ->
            val hasValidId = !course.courseId.isNullOrBlank()
            val hasValidTitle = !course.courseTitle.isNullOrBlank()

            // Get progress records for this specific course
            val courseProgressRecords = allProgressRecords.filter { it.courseId == course.courseId }

            // Count UNIQUE steps that are passed (matches web: step.passed === true)
            val passedStepNumbers = courseProgressRecords
                .filter { it.passed }
                .map { it.stepNum }
                .toSet()
            val passedSteps = passedStepNumbers.size
            val totalSteps = course.courseSteps?.size ?: 0

            // Web logic: ALL steps must be passed AND course must have at least one step
            val allStepsPassed = passedSteps == totalSteps && totalSteps > 0

            // Match web behavior: Show badge if ALL steps are passed AND course has steps
            if (allStepsPassed && hasValidId && hasValidTitle) {
                completedCourses.add(CourseCompletion(course.courseId, course.courseTitle))
            }
        }
        return completedCourses
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
        activitiesRepositoryLazy.get().hasUserCompletedSync(userId)
    }

    private fun insertCourseProgress(mRealm: Realm, act: JsonObject?) {
        val docId = JsonUtils.getString("_id", act)
        val courseId = JsonUtils.getString("courseId", act)
        val userId = JsonUtils.getString("userId", act)
        val stepNum = JsonUtils.getInt("stepNum", act)

        var courseProgress = mRealm.where(RealmCourseProgress::class.java).equalTo("id", docId).findFirst()
        if (courseProgress == null) {
            // Find any local-only record for the same step (pre-upload _id=null, or already
            // uploaded with _id matching this doc) to avoid creating a duplicate.
            val localRecord = mRealm.where(RealmCourseProgress::class.java)
                .equalTo("courseId", courseId)
                .equalTo("userId", userId)
                .equalTo("stepNum", stepNum)
                .beginGroup()
                    .isNull("_id")
                    .or()
                    .equalTo("_id", docId)
                .endGroup()
                .findFirst()
            val localPassed = localRecord?.passed ?: false
            localRecord?.deleteFromRealm()

            courseProgress = mRealm.createObject(RealmCourseProgress::class.java, docId)
            // Preserve a locally-confirmed passed=true in case the server hasn't caught up yet.
            if (localPassed) courseProgress.passed = true
        }
        courseProgress?._id = docId
        courseProgress?._rev = JsonUtils.getString("_rev", act)
        if (courseProgress?.passed != true) {
            courseProgress?.passed = JsonUtils.getBoolean("passed", act)
        }
        courseProgress?.stepNum = stepNum
        courseProgress?.userId = userId
        courseProgress?.parentCode = JsonUtils.getString("parentCode", act)
        courseProgress?.courseId = courseId
        courseProgress?.createdOn = JsonUtils.getString("createdOn", act)
        courseProgress?.createdDate = JsonUtils.getLong("createdDate", act)
        courseProgress?.updatedDate = JsonUtils.getLong("updatedDate", act)
    }

    override fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {
        val documentList = ArrayList<com.google.gson.JsonObject>(jsonArray.size())
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        documentList.forEach { jsonDoc ->
            insertCourseProgress(realm, jsonDoc)
        }
    }
}
