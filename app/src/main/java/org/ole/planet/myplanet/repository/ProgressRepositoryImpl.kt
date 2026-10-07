package org.ole.planet.myplanet.repository

import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.model.CourseCompletion
import org.ole.planet.myplanet.model.CourseProgress
import org.ole.planet.myplanet.model.CourseProgressState
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.CoursesProgressRow
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.KJson

class ProgressRepositoryImpl @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val coursesRepositoryLazy: dagger.Lazy<CoursesRepository>,
    private val activitiesRepositoryLazy: dagger.Lazy<ActivitiesRepository>,
    private val submissionsRepositoryLazy: dagger.Lazy<SubmissionsRepository>,
    private val courseProgressDao: CourseProgressDao,
    private val courseStepDao: CourseStepDao,
    private val examDao: ExamDao,
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
        val arr = mutableListOf<JsonElement>()
        val courseIds = mycourses.mapNotNull { it.courseId }
        val courseProgress = getCourseProgress(courseIds, userId)

        val allExams = if (courseIds.isEmpty()) {
            emptyList()
        } else {
            examDao.getByCourseIds(courseIds)
        }
        val examsByCourseId = allExams.groupBy { it.courseId }
        val courseIdsSet = courseIds.toHashSet()
        val submissionsByCourseId = submissionsRepositoryLazy.get().getExamSubmissionsByUser(userId)
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
            val obj = LinkedHashMap<String, JsonElement>()
            obj["courseName"] = JsonPrimitive(course.courseTitle)
            obj["courseId"] = JsonPrimitive(course.courseId)

            val progressState = courseProgress[course.courseId]
            obj["progress"] = if (progressState != null) {
                buildJsonObject {
                    put("max", progressState.max)
                    put("current", progressState.current)
                }
            } else {
                JsonNull
            }

            val submissions = submissionsByCourseId[course.courseId].orEmpty()

            val exams = examsByCourseId[course.courseId] ?: emptyList()
            val examIds: List<String> = exams.mapNotNull { it.id }

            if (!submissions.isNullOrEmpty()) {
                submissionMap(submissions, examIds, obj)
            }
            arr.add(JsonObject(obj))
        }
        return JsonArray(arr)
    }

    override suspend fun getCourseProgressRows(userId: String?): List<CoursesProgressRow> = withContext(dispatcherProvider.default) {
        val jsonArray = fetchCourseData(userId)
        parseCourseProgressRows(jsonArray)
    }

    private fun parseCourseProgressRows(jsonArray: JsonArray): List<CoursesProgressRow> {
        return jsonArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null

            val courseId = obj["courseId"]?.takeIf { it !is JsonNull }?.let { KJson.gsonAsString(it) }
            val courseName = obj["courseName"]?.takeIf { it !is JsonNull }?.let { KJson.gsonAsString(it) }
            if (courseId == null || courseName == null) return@mapNotNull null

            val progressObj = obj["progress"] as? JsonObject
            val progressCurrent = progressObj?.get("current")?.takeIf { it !is JsonNull }?.let { KJson.gsonAsInt(it) }
            val progressMax = progressObj?.get("max")?.takeIf { it !is JsonNull }?.let { KJson.gsonAsInt(it) }

            val mistakes = obj["mistakes"]?.takeIf { it !is JsonNull }?.let { KJson.gsonAsInt(it) }

            val stepMistakeElem = obj["stepMistake"]
            val stepMistake: Map<String, Int>? = if (stepMistakeElem != null && stepMistakeElem !is JsonNull) {
                KJson.asJsonObject(stepMistakeElem).entries.associate { (key, value) -> key to KJson.gsonAsInt(value) }
            } else {
                null
            }

            CoursesProgressRow(
                courseId = courseId,
                courseName = courseName,
                progressCurrent = progressCurrent,
                progressMax = progressMax,
                mistakes = mistakes,
                stepMistake = stepMistake
            )
        }
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
        submissions: List<Submission>, examIds: List<String>, obj: MutableMap<String, JsonElement>
    ) {
        val examIndexMap = HashMap<String, String>()
        examIds.forEachIndexed { index, id ->
            if (!examIndexMap.containsKey(id)) {
                examIndexMap[id] = index.toString()
            }
        }

        val submissionIds = submissions.mapNotNull { it.id }
        val allAnswers = if (submissionIds.isEmpty()) emptyList() else submissionsRepositoryLazy.get().getAnswersBySubmissionIds(submissionIds)

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
            // In the map's own order, as Gson's toJsonTree wrote it.
            obj["stepMistake"] = JsonObject(mistakesMap.mapValues { (_, count) -> JsonPrimitive(count) })
            obj["mistakes"] = JsonPrimitive(totalMistakes)
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
            val passedSteps = courseProgressRecords
                .mapNotNullTo(HashSet()) { if (it.passed) it.stepNum else null }
                .size
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
        val docId = KJson.getString("_id", act)
        val localPassed = localRecord?.passed ?: false
        val courseProgress = existingProgress
            ?: localRecord
            ?: CourseProgress().apply { id = docId }

        courseProgress.id = docId
        courseProgress._id = docId
        courseProgress._rev = KJson.getString("_rev", act)
        if (courseProgress.passed != true) {
            courseProgress.passed = KJson.getBoolean("passed", act) || localPassed
        }
        courseProgress.stepNum = KJson.getInt("stepNum", act)
        courseProgress.userId = KJson.getString("userId", act)
        courseProgress.parentCode = KJson.getString("parentCode", act)
        courseProgress.courseId = KJson.getString("courseId", act)
        courseProgress.createdOn = KJson.getString("createdOn", act)
        courseProgress.createdDate = KJson.getLong("createdDate", act)
        courseProgress.updatedDate = KJson.getLong("updatedDate", act)
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
                docId = KJson.getString("_id", act),
                courseId = KJson.getString("courseId", act),
                userId = KJson.getString("userId", act),
                stepNum = KJson.getInt("stepNum", act)
            )
        }

        val docIds = syncKeys.mapNotNullTo(LinkedHashSet()) { keys -> keys.docId.takeIf { it.isNotEmpty() } }.toList()
        val requestedTuples = syncKeys.mapNotNull { keys ->
            if (keys.courseId.isNotEmpty() && keys.userId.isNotEmpty()) {
                Triple(keys.courseId, keys.userId, keys.stepNum)
            } else {
                null
            }
        }.distinct()

        val existingProgresses = if (docIds.isNotEmpty()) {
            courseProgressDao.getByIds(docIds).associateBy { it.id }
        } else {
            emptyMap()
        }

        val localRecords = if (requestedTuples.isNotEmpty()) {
            courseProgressDao.getByCourseUsersAndSteps(requestedTuples)
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
            val course = KJson.asJsonObject(element)
            if (KJson.getString("courseId", course) == courseId) {
                return course["progress"]?.let { it as? JsonObject ?: throw ClassCastException("Not a JSON Object: $it") }
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
