package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.room.dao.AnswerDao
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.data.room.dao.SubmissionDao
import org.ole.planet.myplanet.model.CourseCompletion
import org.ole.planet.myplanet.model.CourseProgress
import org.ole.planet.myplanet.model.CourseProgressState
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils

class ProgressRepositoryImpl @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val coursesRepositoryLazy: dagger.Lazy<CoursesRepository>,
    private val activitiesRepositoryLazy: dagger.Lazy<ActivitiesRepository>,
    private val courseProgressDao: CourseProgressDao,
    private val courseStepDao: CourseStepDao,
    private val examDao: ExamDao,
    private val submissionDao: SubmissionDao,
    private val answerDao: AnswerDao,
    private val questionDao: QuestionDao
) : ProgressRepository {
    override suspend fun getCourseProgress(courseIds: List<String>, userId: String?): Map<String, CourseProgressState> = withContext(dispatcherProvider.default) {
        val allSteps = if (courseIds.isEmpty()) {
            emptyList()
        } else {
            courseStepDao.getByCourseIds(courseIds)
        }
        val allProgresses = if (courseIds.isEmpty()) emptyList() else courseProgressDao.getByUserAndCourseIds(userId, courseIds)

        val stepsByCourseId = allSteps.groupBy { it.courseId }
        val progressesByCourseId = allProgresses.groupBy { it.courseId }

        val map = HashMap<String, CourseProgressState>()
        for (courseId in courseIds) {
            val steps = stepsByCourseId[courseId] ?: emptyList()
            val progresses = progressesByCourseId[courseId] ?: emptyList()
            map[courseId] = CourseProgressState(
                max = steps.size,
                current = calculateCurrentProgress(steps, progresses)
            )
        }
        map
    }

    override suspend fun fetchCourseData(userId: String?): JsonArray {
        val mycourses = coursesRepositoryLazy.get().getMyCourses(userId ?: "")
        val arr = JsonArray()
        val courseIds = mycourses.mapNotNull { it.courseId }
        val courseProgress = getCourseProgress(courseIds, userId)

        val allExams = if (courseIds.isEmpty()) {
            emptyList()
        } else {
            examDao.getByCourseIds(courseIds)
        }
        val examsByCourseId = allExams.groupBy { it.courseId }
        val courseIdsSet = courseIds.toHashSet()
        val submissionsByCourseId = submissionDao.getExamSubmissionsByUser(userId)
            .groupBy { submission ->
                val parentId = submission.parentId
                if (parentId != null) {
                    val parts = parentId.split("@")
                    parts.lastOrNull { courseIdsSet.contains(it) }
                } else {
                    null
                }
            }

        mycourses.forEach { course ->
            val obj = JsonObject()
            obj.addProperty("courseName", course.courseTitle)
            obj.addProperty("courseId", course.courseId)

            val progressState = courseProgress[course.courseId]
            if (progressState != null) {
                val progressObj = JsonObject()
                progressObj.addProperty("max", progressState.max)
                progressObj.addProperty("current", progressState.current)
                obj.add("progress", progressObj)
            } else {
                obj.add("progress", null)
            }

            val submissions = submissionsByCourseId[course.courseId].orEmpty()

            val exams = examsByCourseId[course.courseId] ?: emptyList()
            val examIds: List<String> = exams.mapNotNull { it.id }

            if (!submissions.isNullOrEmpty()) {
                submissionMap(submissions, examIds, obj)
            }
            arr.add(obj)
        }
        return arr
    }

    override suspend fun getCurrentProgress(
        steps: List<CourseStep?>?, userId: String?, courseId: String?
    ): Int {
        val progresses = courseProgressDao.getByUserAndCourse(userId, courseId)
        return calculateCurrentProgress(steps, progresses)
    }

    private fun calculateCurrentProgress(
        steps: List<CourseStep?>?, progresses: List<CourseProgress>
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
        while (i <= stepsSize && completed[i]) {
            i++
        }
        return i - 1
    }

    private suspend fun submissionMap(
        submissions: List<Submission>, examIds: List<String>, obj: JsonObject
    ) {
        val examIndexMap = HashMap<String, String>()
        examIds.forEachIndexed { index, id ->
            if (!examIndexMap.containsKey(id)) {
                examIndexMap[id] = index.toString()
            }
        }

        val submissionIds = submissions.mapNotNull { it.id }
        val allAnswers = if (submissionIds.isEmpty()) emptyList() else answerDao.getBySubmissionIds(submissionIds)

        val questionIds = allAnswers.mapNotNull { it.questionId }.distinct()
        val allQuestions = if (questionIds.isEmpty()) emptyList() else questionDao.getByIds(questionIds)
        val questionsMap = allQuestions.associateBy { it.id }

        val answersBySubmissionId = allAnswers.groupBy { it.submissionId }

        var totalMistakes = 0
        submissions.forEach { submission ->
            val answers = answersBySubmissionId[submission.id] ?: emptyList()
            val mistakesMap = HashMap<String, Int>()
            answers.forEach { r ->
                r.questionId?.let { questionId ->
                    val question = questionsMap[questionId]
                    if (question != null) {
                        val examIndexKey = examIndexMap[question.examId]
                        if (examIndexKey != null) {
                            totalMistakes += r.mistakes
                            mistakesMap[examIndexKey] = (mistakesMap[examIndexKey] ?: 0) + r.mistakes
                        }
                    }
                }
            }
            obj.add("stepMistake", JsonUtils.gson.toJsonTree(mistakesMap).asJsonObject)
            obj.addProperty("mistakes", totalMistakes)
        }
    }

    override suspend fun getProgressRecords(userId: String?): List<CourseProgress> {
        return courseProgressDao.getByUser(userId)
    }

    override suspend fun getCompletedCourses(userId: String): List<CourseCompletion> {
        val myCourses = coursesRepositoryLazy.get().getMyCourses(userId)
        val allProgressRecords = getProgressRecords(userId)

        val progressByCourse = allProgressRecords.groupBy { it.courseId }

        val completedCourses = mutableListOf<CourseCompletion>()
        myCourses.forEach { course ->
            val hasValidId = !course.courseId.isNullOrBlank()
            val hasValidTitle = !course.courseTitle.isNullOrBlank()

            // Get progress records for this specific course
            val courseProgressRecords = progressByCourse[course.courseId].orEmpty()

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
        val now = Date().time
        val courseProgress = courseProgressDao.findByCourseUserAndStep(courseId, userId, stepNum)
            ?: CourseProgress().apply {
                id = UUID.randomUUID().toString()
                createdDate = now
            }
        courseProgress.courseId = courseId
        courseProgress.stepNum = stepNum
        if (passed != null) {
            courseProgress.passed = passed
        }
        courseProgress.createdOn = planetCode
        courseProgress.updatedDate = now
        courseProgress.parentCode = parentCode
        courseProgress.userId = userId
        courseProgressDao.upsert(courseProgress)
    }

    override suspend fun hasUserCompletedSync(userId: String): Boolean = withContext(dispatcherProvider.io) {
        activitiesRepositoryLazy.get().hasUserCompletedSync(userId)
    }

    private fun courseProgressFromJson(
        act: JsonObject,
        existingProgress: CourseProgress?,
        localRecord: CourseProgress?
    ): CourseProgress {
        val docId = JsonUtils.getString("_id", act)
        val localPassed = localRecord?.passed ?: false
        val courseProgress = existingProgress
            ?: localRecord
            ?: CourseProgress().apply { id = docId }

        courseProgress.id = docId
        courseProgress._id = docId
        courseProgress._rev = JsonUtils.getString("_rev", act)
        if (courseProgress.passed != true) {
            courseProgress.passed = JsonUtils.getBoolean("passed", act) || localPassed
        }
        courseProgress.stepNum = JsonUtils.getInt("stepNum", act)
        courseProgress.userId = JsonUtils.getString("userId", act)
        courseProgress.parentCode = JsonUtils.getString("parentCode", act)
        courseProgress.courseId = JsonUtils.getString("courseId", act)
        courseProgress.createdOn = JsonUtils.getString("createdOn", act)
        courseProgress.createdDate = JsonUtils.getLong("createdDate", act)
        courseProgress.updatedDate = JsonUtils.getLong("updatedDate", act)
        return courseProgress
    }

    private data class CourseProgressSyncKeys(
        val doc: JsonObject,
        val docId: String,
        val courseId: String,
        val userId: String,
        val stepNum: Int
    )

    override suspend fun insertCourseProgressFromSync(docs: List<JsonObject>) {
        val syncKeys = docs.map { act ->
            CourseProgressSyncKeys(
                doc = act,
                docId = JsonUtils.getString("_id", act),
                courseId = JsonUtils.getString("courseId", act),
                userId = JsonUtils.getString("userId", act),
                stepNum = JsonUtils.getInt("stepNum", act)
            )
        }

        val docIds = syncKeys.mapNotNullTo(LinkedHashSet()) { keys -> keys.docId.takeIf { it.isNotEmpty() } }.toList()
        val courseIds = syncKeys.mapNotNullTo(LinkedHashSet()) { keys -> keys.courseId.takeIf { it.isNotEmpty() } }.toList()
        val userIds = syncKeys.mapNotNullTo(LinkedHashSet()) { keys -> keys.userId.takeIf { it.isNotEmpty() } }.toList()
        val stepNums = syncKeys.mapTo(LinkedHashSet()) { keys -> keys.stepNum }.toList()

        val existingProgresses = if (docIds.isNotEmpty()) {
            courseProgressDao.getByIds(docIds).associateBy { it.id }
        } else {
            emptyMap()
        }

        val localRecords = if (courseIds.isNotEmpty() && userIds.isNotEmpty() && stepNums.isNotEmpty()) {
            courseProgressDao.getByCourseUsersAndSteps(courseIds, userIds, stepNums)
        } else {
            emptyList()
        }

        val localRecordsByKey = localRecords.groupBy { Triple(it.courseId, it.userId, it.stepNum) }

        val progress = syncKeys.map { keys ->
            val existingProgress = existingProgresses[keys.docId]
            val localRecord = if (existingProgress == null) {
                localRecordsByKey[Triple<String?, String?, Int>(keys.courseId, keys.userId, keys.stepNum)]
                    ?.find { it._id == null || it._id == keys.docId }
            } else {
                null
            }
            courseProgressFromJson(keys.doc, existingProgress, localRecord)
        }

        if (progress.isNotEmpty()) {
            courseProgressDao.upsertAll(progress)
        }
    }

    override fun findProgressForCourse(courseData: JsonArray, courseId: String): JsonObject? {
        courseData.forEach { element ->
            val course = element.asJsonObject
            if (JsonUtils.getString("courseId", course) == courseId) {
                return course.getAsJsonObject("progress")
            }
        }
        return null
    }

    override suspend fun getPendingCourseProgressUploads(): List<CourseProgress> {
        return courseProgressDao.getPendingUploads()
    }

    override suspend fun markCourseProgressUploaded(localId: String, remoteId: String, rev: String): Boolean {
        return courseProgressDao.markUploaded(localId, remoteId, rev) != 0
    }
}
