package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.AnswerDao
import org.ole.planet.myplanet.data.room.dao.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.data.room.dao.SubmissionDao
import org.ole.planet.myplanet.model.CourseCompletion
import org.ole.planet.myplanet.model.Answer
import org.ole.planet.myplanet.model.CourseProgress
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.StepExam
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
    override suspend fun getCourseProgress(courseIds: List<String>, userId: String?): HashMap<String?, JsonObject> {
        val allSteps = if (courseIds.isEmpty()) {
            emptyList()
        } else {
            courseStepDao.getByCourseIds(courseIds).map { it }
        }
        val allProgresses = if (courseIds.isEmpty()) emptyList() else courseProgressDao.getByUserAndCourseIds(userId, courseIds)

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

        val allExams = if (courseIds.isEmpty()) {
            emptyList()
        } else {
            examDao.getByCourseIds(courseIds).map { it }
        }
        val examsByCourseId = allExams.groupBy { it.courseId }
        val submissionsByCourseId = submissionDao.getExamSubmissionsByUser(userId)
            .map { it }
            .groupBy { submission -> courseIds.firstOrNull { courseId -> submission.parentId?.contains(courseId) == true } }

        mycourses.forEach { course ->
            val obj = JsonObject()
            obj.addProperty("courseName", course.courseTitle)
            obj.addProperty("courseId", course.courseId)
            obj.add("progress", courseProgress[course.courseId])

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
        val submissionIds = submissions.mapNotNull { it.id }
        val allAnswers = if (submissionIds.isEmpty()) emptyList() else answerDao.getBySubmissionIds(submissionIds).map { it }

        val questionIds = allAnswers.mapNotNull { it.questionId }.distinct()
        val allQuestions = if (questionIds.isEmpty()) emptyList() else questionDao.getByIds(questionIds).map { it }
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

    override suspend fun insertCourseProgressFromSync(docs: List<JsonObject>) {
        val docIds = docs.map { JsonUtils.getString("_id", it) }.filter { it.isNotEmpty() }.distinct()
        val courseIds = docs.map { JsonUtils.getString("courseId", it) }.filter { it.isNotEmpty() }.distinct()
        val userIds = docs.map { JsonUtils.getString("userId", it) }.filter { it.isNotEmpty() }.distinct()
        val stepNums = docs.map { JsonUtils.getInt("stepNum", it) }.distinct()

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

        val progress = docs.map { act ->
            val docId = JsonUtils.getString("_id", act)
            val courseId = JsonUtils.getString("courseId", act)
            val userId = JsonUtils.getString("userId", act)
            val stepNum = JsonUtils.getInt("stepNum", act)
            val existingProgress = existingProgresses[docId]
            val localRecord = if (existingProgress == null) {
                localRecordsByKey[Triple<String?, String?, Int>(courseId, userId, stepNum)]
                    ?.find { it._id == null || it._id == docId }
            } else {
                null
            }
            courseProgressFromJson(act, existingProgress, localRecord)
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
}
