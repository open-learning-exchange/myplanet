package org.ole.planet.myplanet.repository

import android.util.Base64
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.data.room.dao.AnswerDao
import org.ole.planet.myplanet.data.room.dao.CertificationDao
import org.ole.planet.myplanet.data.room.dao.CourseDao
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.data.room.dao.SubmissionDao
import org.ole.planet.myplanet.model.Answer
import org.ole.planet.myplanet.model.Certification
import org.ole.planet.myplanet.model.CourseDetailModel
import org.ole.planet.myplanet.model.CourseProgressData
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.CourseStepData
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.RemovedLog
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.StepItem
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utils.ExamAnswerUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

class CoursesRepositoryImpl @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val activitiesRepository: ActivitiesRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val tagsRepository: TagsRepository,
    private val ratingsRepository: RatingsRepository,
    private val resourcesRepository: ResourcesRepository,
    private val sharedPrefManager: SharedPrefManager,
    private val certificationDao: CertificationDao,
    private val courseDao: CourseDao,
    private val courseStepDao: CourseStepDao,
    private val examDao: ExamDao,
    private val questionDao: QuestionDao,
    private val submissionDao: SubmissionDao,
    private val answerDao: AnswerDao,
    private val searchActivityDao: SearchActivityDao,
    private val courseProgressDao: CourseProgressDao,
    private val removedLogDao: RemovedLogDao,
    private val myLibraryDao: MyLibraryDao,
    private val userRepository: dagger.Lazy<UserRepository>,
    private val dispatcherProvider: DispatcherProvider,
    private val realtimeSyncManager: RealtimeSyncManager
) : CoursesRepository {

    private val pendingCourseResources =
        java.util.Collections.synchronizedList(mutableListOf<PendingCourseResource>())

    private data class PendingCourseResource(
        val doc: JsonObject,
        val courseId: String?,
        val stepId: String?
    )

    private data class ParsedCourseSyncPayload(
        val course: MyCourse,
        val steps: List<CourseStep>,
        val exams: List<StepExam>,
        val questions: List<ExamQuestion>
    )

    // Shelf membership is stored as a JSON userId list; match a single entry with LIKE %"id"%.
    private fun userIdPattern(userId: String): String {
        val escaped = userId
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%\"$escaped\"%"
    }

    override suspend fun getAllCourses(): List<MyCourse> {
        return mapCourses(courseDao.getAll())
            .filter { !it.courseTitle.isNullOrEmpty() }
    }

    override fun getMyCourses(userId: String?, courses: List<MyCourse>): List<MyCourse> {
        if (userId == null) return emptyList()
        return courses.filter { it.userId?.contains(userId) == true }
    }

    override suspend fun getMyCourses(userId: String): List<MyCourse> {
        return mapCourses(courseDao.getForUserPattern(userIdPattern(userId)))
    }

    override suspend fun getMyCoursesFlow(userId: String): Flow<List<MyCourse>> {
        return courseDao.observeForUserPattern(userIdPattern(userId)).map { courses ->
            mapCourses(courses)
        }.distinctUntilChanged { old, new ->
            old.size == new.size && old.zip(new).all { (a, b) ->
                a.id == b.id && a.courseRev == b.courseRev && a.userId == b.userId
            }
        }.flowOn(dispatcherProvider.default)
    }

    override suspend fun getCourseById(courseId: String): MyCourse? {
        if (courseId.isBlank()) return null
        return mapCourse(courseDao.getByCourseId(courseId))
    }

    override fun getCourseDetailModel(courseId: String): Flow<CourseDetailModel?> {
        return getCourseByCourseIdFlow(courseId).map { course ->
            if (course == null) return@map null

            val user = userRepository.get().getUserModel()
            val examCount = getCourseExamCount(courseId)
            val resources = getCourseOnlineResources(courseId)
            val downloadedResources = getCourseOfflineResources(courseId)
            val rawSteps = getCourseSteps(courseId)

            val steps = rawSteps.map { step ->
                val count = step.id.let { submissionsRepository.getExamQuestionCount(it) }
                StepItem(
                    id = step.id,
                    stepTitle = step.stepTitle,
                    questionCount = count
                )
            }

            val userId = user?.id
            val ratingSummary = if (userId != null) {
                ratingsRepository.getRatingSummary("course", courseId, userId)
            } else {
                null
            }

            CourseDetailModel(
                course = course,
                user = user,
                ratingSummary = ratingSummary,
                examCount = examCount,
                resources = resources,
                downloadedResources = downloadedResources,
                steps = steps
            )
        }.flowOn(dispatcherProvider.io)
    }

    override fun getCourseByCourseIdFlow(courseId: String): Flow<MyCourse?> {
        return courseDao.observeByCourseId(courseId).map { course ->
            mapCourse(course)
        }.flowOn(dispatcherProvider.default)
    }

    override suspend fun getCoursesByIds(courseIds: List<String>): List<MyCourse> {
        if (courseIds.isEmpty()) return emptyList()
        return mapCourses(courseDao.getByCourseIds(courseIds))
    }

    private suspend fun getCourseOnlineResources(courseId: String?): List<MyLibrary> {
        return getCourseResources(courseId, isOffline = false)
    }

    override suspend fun getCourseOfflineResources(courseId: String?): List<MyLibrary> {
        return getCourseResources(courseId, isOffline = true)
    }

    override suspend fun getCourseOfflineResources(courseIds: List<String>): List<MyLibrary> {
        if (courseIds.isEmpty()) {
            return emptyList()
        }
        return myLibraryDao.getOfflineResourcesForCourses(courseIds)
    }

    private suspend fun getCourseExamCount(courseId: String?): Int {
        if (courseId.isNullOrEmpty()) {
            return 0
        }
        return examDao.countByCourseIdAndType(courseId, "courses")
    }

    override suspend fun getCourseSteps(courseId: String): List<CourseStep> {
        if (courseId.isBlank()) {
            return emptyList()
        }
        return courseStepDao.getByCourseId(courseId)
    }

    override suspend fun markCoursesAdded(courseIds: List<String>, userId: String?): Result<Boolean> {
        return runCatching {
            val validCourseIds = courseIds.filter { it.isNotBlank() }.distinct()
            if (validCourseIds.isEmpty()) return@runCatching false

            val courses = validCourseIds.chunked(300).flatMap { chunk ->
                courseDao.getByCourseIds(chunk)
            }.distinctBy { it.id }

            if (courses.isEmpty()) {
                return@runCatching false
            }

            courseDao.upsertAll(
                courses.map { course ->
                    course.copy(userId = mergeUserIds(course.userId, userId))
                }
            )

            if (!userId.isNullOrBlank()) {
                val idsToDelete = mutableSetOf<String>()
                idsToDelete.addAll(validCourseIds)
                courses.forEach { course ->
                    course.courseId?.takeIf { it.isNotBlank() }?.let { idsToDelete.add(it) }
                    course.id.takeIf { it.isNotBlank() }?.let { idsToDelete.add(it) }
                    course._id?.takeIf { it.isNotBlank() }?.let { idsToDelete.add(it) }
                }
                removedLogDao.deleteByTypeUserAndDocsChunked("courses", userId, idsToDelete.toList())
            }

            realtimeSyncManager.notifyTableUpdated(TableDataUpdate("courses", 0, courses.size))
            true
        }
    }

    private suspend fun getCourseResources(courseId: String?, isOffline: Boolean): List<MyLibrary> {
        if (courseId.isNullOrEmpty()) {
            return emptyList()
        }
        return myLibraryDao.getCourseResources(courseId, isOffline)
    }

    internal fun matchesAllParts(title: String, parts: List<String>): Boolean {
        return parts.all { title.contains(it) }
    }

    override suspend fun search(query: String): List<MyCourse> {
        val allCourses = mapCourses(courseDao.getAll())
        if (query.isEmpty()) {
            return allCourses
        }

        val queryParts = query.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(query)

        val data = allCourses.filter { course ->
            val title = course.courseTitleNormal ?: course.courseTitle?.let { Utilities.normalizeText(it) }
            title != null && normalizedQueryParts.all { title.contains(it) }
        }

        val startsWithQuery = mutableListOf<MyCourse>()
        val containsQuery = mutableListOf<MyCourse>()

        for (item in data) {
            val title = item.courseTitleNormal ?: item.courseTitle?.let { Utilities.normalizeText(it) } ?: continue

            if (title.startsWith(normalizedQuery)) {
                startsWithQuery.add(item)
            } else if (matchesAllParts(title, normalizedQueryParts)) {
                containsQuery.add(item)
            }
        }
        return startsWithQuery + containsQuery
    }

    override suspend fun filterCourses(
        searchText: String,
        gradeLevel: String,
        subjectLevel: String,
        tagNames: List<String>
    ): List<MyCourse> {
        val courseIdsWithTags = if (tagNames.isNotEmpty()) {
            tagsRepository.getLinkIdsForTagNames("courses", tagNames).toSet()
        } else {
            null
        }

        if (tagNames.isNotEmpty() && courseIdsWithTags.isNullOrEmpty()) {
            return emptyList()
        }

        return mapCourses(courseDao.getAll())
            .asSequence()
            .filter { !it.courseTitle.isNullOrEmpty() }
            .filter { searchText.isEmpty() || it.courseTitle?.contains(searchText, ignoreCase = true) == true }
            .filter { gradeLevel.isEmpty() || it.gradeLevel == gradeLevel }
            .filter { subjectLevel.isEmpty() || it.subjectLevel == subjectLevel }
            .filter { courseIdsWithTags == null || courseIdsWithTags.contains(it.courseId) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.courseTitle ?: "" })
            .toList()
    }

    override suspend fun saveSearchActivity(
        searchText: String,
        userName: String,
        planetCode: String,
        parentCode: String,
        tags: List<TagEntity>,
        grade: String,
        subject: String
    ) {
        val filter = JsonObject().apply {
            add("tags", TagEntity.getTagsArray(tags))
            addProperty("doc.gradeLevel", grade)
            addProperty("doc.subjectLevel", subject)
        }
        searchActivityDao.insert(
            SearchActivity(
                id = UUID.randomUUID().toString(),
                user = userName,
                time = Calendar.getInstance().timeInMillis,
                createdOn = planetCode,
                parentCode = parentCode,
                text = searchText,
                type = "courses",
                filter = JsonUtils.gson.toJson(filter)
            )
        )
    }

    override suspend fun joinCourse(courseId: String, userId: String): Result<Unit> {
        return runCatching {
            if (courseId.isBlank() || userId.isBlank()) return@runCatching

            val course = courseDao.getByCourseId(courseId)
            if (course != null) {
                courseDao.upsert(course.copy(userId = mergeUserIds(course.userId, userId)))
            }
            val idsToDelete = mutableSetOf(courseId)
            if (course != null) {
                course.courseId?.takeIf { it.isNotBlank() }?.let { idsToDelete.add(it) }
                course.id.takeIf { it.isNotBlank() }?.let { idsToDelete.add(it) }
                course._id?.takeIf { it.isNotBlank() }?.let { idsToDelete.add(it) }
            }
            removedLogDao.deleteByTypeUserAndDocsChunked("courses", userId, idsToDelete.toList())
            realtimeSyncManager.notifyTableUpdated(TableDataUpdate("courses", 0, 1))
        }
    }

    override suspend fun leaveCourse(courseId: String, userId: String): Result<Unit> {
        return leaveCourses(listOf(courseId), userId)
    }

    override suspend fun leaveCourses(courseIds: List<String>, userId: String): Result<Unit> {
        return runCatching {
            val validCourseIds = courseIds.filter { it.isNotBlank() }.distinct()
            if (validCourseIds.isEmpty()) return@runCatching

            val courses = validCourseIds.chunked(300).flatMap { chunk ->
                courseDao.getByCourseIds(chunk)
            }.distinctBy { it.id }

            if (courses.isNotEmpty()) {
                val updatedCourses = courses.map { course ->
                    val updatedUserIds = course.userId.orEmpty().filter { it != userId }
                    course.copy(userId = updatedUserIds)
                }
                courseDao.upsertAll(updatedCourses)
            }

            if (userId.isNotBlank()) {
                val logsToInsert = mutableMapOf<String, RemovedLog>()

                if (courses.isNotEmpty()) {
                    courses.forEach { course ->
                        val canonicalId = course.courseId?.takeIf { it.isNotBlank() }
                            ?: course.id.takeIf { it.isNotBlank() }
                            ?: course._id
                        if (!canonicalId.isNullOrBlank()) {
                            logsToInsert[canonicalId] = RemovedLog().apply {
                                id = UUID.randomUUID().toString()
                                type = "courses"
                                this.userId = userId
                                this.docId = canonicalId
                            }
                        }
                    }
                }

                validCourseIds.forEach { docId ->
                    if (!logsToInsert.containsKey(docId)) {
                        logsToInsert[docId] = RemovedLog().apply {
                            id = UUID.randomUUID().toString()
                            type = "courses"
                            this.userId = userId
                            this.docId = docId
                        }
                    }
                }

                logsToInsert.values.toList().chunked(1000).forEach { chunk ->
                    removedLogDao.insertAll(chunk)
                }
            }

            val finalCount = if (courses.isNotEmpty()) courses.size else validCourseIds.size
            realtimeSyncManager.notifyTableUpdated(TableDataUpdate("courses", 0, finalCount))
        }
    }

    override suspend fun isMyCourse(userId: String?, courseId: String?): Boolean {
        if (userId.isNullOrBlank() || courseId.isNullOrBlank()) {
            return false
        }
        return courseDao.getByCourseId(courseId)?.userId?.contains(userId) == true
    }

    override suspend fun getCourseProgress(courseId: String, userId: String?): CourseProgressData {
        val stepsList = getCourseSteps(courseId)
        val current = progressRepository.getCurrentProgress(stepsList, userId, courseId)
        val courseTitle = getCourseById(courseId)?.courseTitle
        val stepIds = stepsList.map { it.id }
        val allExams = if (stepIds.isEmpty()) emptyList() else examDao.getByStepIds(stepIds)
        val max = stepsList.size
        val examsByStepId = allExams.groupBy { it.stepId }

        val examIds = allExams.map { it.id }
        val questionsByExamId = if (examIds.isEmpty()) {
            emptyMap()
        } else {
            questionDao.getByExamIds(examIds)
                .map { it }
                .groupBy { it.examId ?: "" }
                .filterKeys { it.isNotEmpty() }
        }

        val examIdsSet = examIds.toSet()
        val relevantSubmissions = submissionDao.getExamSubmissionsByUser(userId)
            .map { it }
            .filter { sub -> examIdsSet.contains(getParentBaseId(sub.parentId)) }

        val submissionsByExamId = relevantSubmissions.groupBy { sub ->
            getParentBaseId(sub.parentId).orEmpty()
        }.filterKeys { it.isNotEmpty() }

        val submissionIds = relevantSubmissions.map { it.id }
        val answersBySubmissionId = if (submissionIds.isEmpty()) {
            emptyMap()
        } else {
            answerDao.getBySubmissionIds(submissionIds)
                .map { it }
                .groupBy { it.submissionId ?: "" }
                .filterKeys { it.isNotEmpty() }
        }

        val array = JsonArray()
        stepsList.forEach { step ->
            val ob = JsonObject()
            ob.addProperty("stepId", step.id)
            val exams = examsByStepId[step.id] ?: emptyList()
            getExamObject(exams, ob, questionsByExamId, submissionsByExamId, answersBySubmissionId)
            array.add(ob)
        }
        return CourseProgressData(courseTitle, current, max, array)
    }

    private fun getParentBaseId(parentId: String?): String? {
        return if (parentId?.contains("@") == true) parentId.split("@")[0] else parentId
    }

    private fun getExamObject(
        exams: Iterable<StepExam>,
        ob: JsonObject,
        questionsByExamId: Map<String, List<ExamQuestion>>,
        submissionsByExamId: Map<String, List<Submission>>,
        answersBySubmissionId: Map<String, List<Answer>>
    ) {
        exams.forEach { exam ->
            exam.id.let { examId ->
                val submissionsForExam = submissionsByExamId[examId] ?: emptyList()
                submissionsForExam.forEach { submission ->
                    val answers = submission.id.let { answersBySubmissionId[it] } ?: emptyList()
                    val questions = questionsByExamId[examId] ?: emptyList()
                    val questionCount = questions.size
                    if (questionCount == 0) {
                        if (!ob.has("completed")) ob.addProperty("completed", false)
                        if (!ob.has("percentage")) ob.addProperty("percentage", 0)
                    } else {
                        ob.addProperty("completed", answers.size == questionCount)
                        val percentage = (answers.size.toDouble() / questionCount) * 100
                        ob.addProperty("percentage", percentage)
                    }
                    ob.addProperty("status", submission.status)
                }
            }
        }
    }

    override suspend fun batchInsertMyCourses(shelfId: String?, documents: List<JsonObject>): Int {
        val processedCount = upsertRoomCoursesFromSync(documents, shelfId, continueOnError = true)
        MyCourse.saveConcatenatedLinksToPrefs(sharedPrefManager)
        flushPendingCourseResources()
        return processedCount
    }

    override suspend fun getCourseTitleById(courseId: String): String? {
        return getCourseById(courseId)?.courseTitle
    }

    override suspend fun isCourseCertified(courseId: String): Boolean {
        if (courseId.isBlank()) return false
        return certificationDao.countByCourseId(courseId) > 0
    }

    override suspend fun updateCourseProgress(courseId: String?, stepNum: Int, passed: Boolean) {
        if (courseId.isNullOrEmpty()) return
        courseProgressDao.updatePassedByCourseAndStep(courseId, stepNum, passed)
    }

    override suspend fun getCourseStepData(stepId: String, userId: String?): CourseStepData {
        val step = courseStepDao.getById(stepId)
            ?: throw IllegalStateException("Step not found")
        val resources = myLibraryDao.getByStepId(stepId)
        val stepExams = examDao.getByStepIdAndType(stepId, "courses").map { it }
        val stepSurvey = examDao.getByStepIdAndType(stepId, "surveys").map { it }
        val userHasCourse = isMyCourse(userId, step.courseId)

        val hasExam = if (stepExams.isNotEmpty()) {
            val firstStepId = stepExams[0].id
            submissionsRepository.hasSubmission(firstStepId, step.courseId, userId, "exam")
        } else false

        val hasSurvey = if (stepSurvey.isNotEmpty()) {
            val firstStepId = stepSurvey[0].id
            submissionsRepository.hasSubmission(firstStepId, step.courseId, userId, "survey")
        } else false

        return CourseStepData(
            step = step,
            resources = resources,
            stepExams = stepExams,
            stepSurvey = stepSurvey,
            userHasCourse = userHasCourse,
            hasExam = hasExam,
            hasSurvey = hasSurvey
        )
    }

    override suspend fun getMyCourseIds(userId: String): JsonArray {
        val ids = JsonArray()
        getMyCourses(userId).mapNotNull { it.courseId }.forEach { ids.add(it) }
        return ids
    }

    override suspend fun removeCourseFromShelf(courseId: String, userId: String) {
        leaveCourse(courseId, userId)
    }

    override suspend fun removeCoursesFromShelf(courseIds: List<String>, userId: String) {
        leaveCourses(courseIds, userId).getOrThrow()
    }

    override suspend fun logCourseVisit(courseId: String, title: String, userId: String) {
        activitiesRepository.logCourseVisit(courseId, title, userId)
    }

    override suspend fun getCurrentProgress(steps: List<CourseStep?>?, userId: String?, courseId: String?): Int {
        return progressRepository.getCurrentProgress(steps, userId, courseId)
    }

    override suspend fun isStepCompleted(stepId: String?, userId: String?): Boolean {
        return submissionsRepository.isStepCompleted(stepId, userId)
    }

    override suspend fun hasUnfinishedSurveys(courseId: String, userId: String?): Boolean {
        return submissionsRepository.hasUnfinishedSurveys(courseId, userId)
    }

    override suspend fun getCourseTagsBulk(courseIds: List<String>): Map<String, List<TagEntity>> {
        return tagsRepository.getTagsForCourses(courseIds)
    }

    override suspend fun deleteCourseProgress(courseId: String?) {
        val examIds = courseId?.let { examDao.getByCourseId(it).map { exam -> exam.id } }.orEmpty()
        if (examIds.isNotEmpty()) {
            val submissions = submissionDao.getUnuploadedNonSurveyByParentIds(examIds)
            val submissionIds = submissions.map { it.id }
            if (submissionIds.isNotEmpty()) {
                answerDao.deleteBySubmissionIds(submissionIds)
                submissionDao.deleteByIds(submissionIds)
            }
        }
    }

    override suspend fun bulkInsertFromSync(jsonArray: JsonArray) {
        val documentList = ArrayList<JsonObject>(jsonArray.size())
        for (j in jsonArray) {
            val jsonDoc = JsonUtils.getJsonObject("doc", j.asJsonObject)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        upsertRoomCoursesFromSync(documentList)
        MyCourse.saveConcatenatedLinksToPrefs(sharedPrefManager)
    }

    private suspend fun upsertRoomCoursesFromSync(
        documentList: List<JsonObject>,
        shelfId: String? = null,
        continueOnError: Boolean = false
    ): Int {
        if (documentList.isEmpty()) return 0

        val existingCourses = courseDao.getByCourseIds(
            documentList.mapNotNull { JsonUtils.getString("_id", it).takeIf(String::isNotBlank) }
        ).associateBy { it.courseId ?: it.id }

        val courses = ArrayList<MyCourse>(documentList.size)
        val steps = ArrayList<CourseStep>()
        val exams = ArrayList<StepExam>()
        val questions = ArrayList<ExamQuestion>()
        var processedCount = 0

        documentList.forEach { doc ->
            try {
                val payload = buildCoursePayload(doc, shelfId, existingCourses)
                if (payload != null) {
                    processedCount++
                    courses.add(payload.course)
                    steps.addAll(payload.steps)
                    exams.addAll(payload.exams)
                    questions.addAll(payload.questions)
                }
            } catch (e: Exception) {
                if (!continueOnError) throw e
                e.printStackTrace()
            }
        }

        if (courses.isEmpty() && steps.isEmpty() && exams.isEmpty() && questions.isEmpty()) return processedCount

        if (courses.isNotEmpty()) courseDao.upsertAll(courses)
        if (steps.isNotEmpty()) courseStepDao.upsertAll(steps)
        if (exams.isNotEmpty()) examDao.upsertAll(exams)
        if (questions.isNotEmpty()) questionDao.upsertAll(questions)
        return processedCount
    }

    private fun buildCoursePayload(
        doc: JsonObject,
        shelfId: String?,
        existingCourses: Map<String, MyCourse>
    ): ParsedCourseSyncPayload? {
        val courseId = JsonUtils.getString("_id", doc)
        if (courseId.isBlank()) return null

        val existingCourse = existingCourses[courseId]
        val title = JsonUtils.getString("courseTitle", doc)
        val description = JsonUtils.getString("description", doc)
        val baseUrl = UrlUtils.getUrl()
        extractLinks(description).forEach { link ->
            MyCourse.addConcatenatedLink("$baseUrl/$link")
        }

        val stepIds = mutableListOf<String>()
        val parsedSteps = ArrayList<CourseStep>()
        val parsedExams = ArrayList<StepExam>()
        val parsedQuestions = ArrayList<ExamQuestion>()
        val stepsJson = JsonUtils.getJsonArray("steps", doc)
        for (i in 0 until stepsJson.size()) {
            val stepElement = stepsJson[i]
            val stepId = Base64.encodeToString(stepElement.toString().toByteArray(), Base64.NO_WRAP)
            val stepJson = stepElement.asJsonObject
            val stepDescription = JsonUtils.getString("description", stepJson)
            extractLinks(stepDescription).forEach { link ->
                MyCourse.addConcatenatedLink("$baseUrl/$link")
            }
            queueCourseResources(courseId, stepId, JsonUtils.getJsonArray("resources", stepJson))
            stepIds.add(stepId)
            parsedSteps.add(
                CourseStep(
                    id = stepId,
                    courseId = courseId,
                    stepTitle = JsonUtils.getString("stepTitle", stepJson),
                    description = stepDescription,
                    noOfResources = JsonUtils.getJsonArray("resources", stepJson).size(),
                )
            )
            collectRoomExam(stepJson, "exam", courseId, stepId, parsedExams, parsedQuestions)
            collectRoomExam(stepJson, "survey", courseId, stepId, parsedExams, parsedQuestions)
        }

        val course = MyCourse(
            id = existingCourse?.id ?: courseId,
            _id = courseId,
            courseRev = JsonUtils.getString("_rev", doc),
            courseId = courseId,
            courseTitle = title,
            courseTitleNormal = Utilities.normalizeText(title),
            description = description,
            userId = mergeUserIds(existingCourse?.userId, shelfId),
            languageOfInstruction = JsonUtils.getString("languageOfInstruction", doc),
            memberLimit = JsonUtils.getInt("memberLimit", doc),
            method = JsonUtils.getString("method", doc),
            gradeLevel = JsonUtils.getString("gradeLevel", doc),
            subjectLevel = JsonUtils.getString("subjectLevel", doc),
            createdDate = JsonUtils.getLong("createdDate", doc),
                        coverFileName = JsonUtils.getString("coverFileName", doc).takeIf { it.isNotEmpty() },
                    )

        return ParsedCourseSyncPayload(course, parsedSteps, parsedExams, parsedQuestions)
    }

    private fun collectRoomExam(
        stepJson: JsonObject,
        examKey: String,
        courseId: String,
        stepId: String,
        exams: MutableList<StepExam>,
        questions: MutableList<ExamQuestion>
    ) {
        if (!stepJson.has(examKey)) return
        val examJson = stepJson.getAsJsonObject(examKey)
        val examId = JsonUtils.getString("_id", examJson).ifBlank { "$courseId-$stepId-$examKey" }
        val questionArray = JsonUtils.getJsonArray("questions", examJson)
        exams.add(
            StepExam(
                id = examId,
                _rev = JsonUtils.getString("_rev", examJson),
                createdDate = JsonUtils.getLong("createdDate", examJson),
                updatedDate = JsonUtils.getLong("updatedDate", examJson),
                adoptionDate = JsonUtils.getLong("adoptionDate", examJson),
                createdBy = JsonUtils.getString("createdBy", examJson),
                totalMarks = JsonUtils.getInt("totalMarks", examJson),
                name = JsonUtils.getString("name", examJson),
                description = JsonUtils.getString("description", examJson),
                type = if (examJson.has("type")) JsonUtils.getString("type", examJson) else examKey,
                stepId = stepId,
                courseId = courseId,
                sourcePlanet = JsonUtils.getString("sourcePlanet", examJson),
                passingPercentage = JsonUtils.getString("passingPercentage", examJson),
                noOfQuestions = questionArray.size(),
                teamId = JsonUtils.getString("teamId", examJson),
                isTeamShareAllowed = JsonUtils.getBoolean("teamShareAllowed", examJson),
                sourceSurveyId = JsonUtils.getString("sourceSurveyId", examJson),
            )
        )
        for (i in 0 until questionArray.size()) {
            val questionJson = questionArray[i].asJsonObject
            val questionId = JsonUtils.getString("id", questionJson).ifBlank { "$examId-$i" }
            questions.add(
                ExamQuestion(
                    id = questionId,
                    examId = examId,
                    type = JsonUtils.getString("type", questionJson),
                    header = JsonUtils.getString("title", questionJson),
                    body = JsonUtils.getString("body", questionJson).ifBlank { JsonUtils.getString("title", questionJson) },
                    choices = if (questionJson.has("choices")) {
                        JsonUtils.gson.toJson(JsonUtils.getJsonArray("choices", questionJson))
                    } else {
                        "[]"
                    },
                                        hasOtherOption = JsonUtils.getBoolean("hasOtherOption", questionJson),
                    scaleMax = JsonUtils.getInt("scaleMax", questionJson).let { if (it <= 0) 9 else it },
                    marks = JsonUtils.getString("marks", questionJson),
                    correctChoiceList = extractCorrectChoices(questionJson),
                )
            )
        }
    }

    override suspend fun insertCertificationsFromSync(jsonArray: JsonArray) {
        val certifications = ArrayList<Certification>(jsonArray.size())
        for (j in jsonArray) {
            val jsonDoc = JsonUtils.getJsonObject("doc", j.asJsonObject)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (id.startsWith("_design")) continue
            certifications.add(
                Certification().apply {
                    _id = id
                    name = JsonUtils.getString("name", jsonDoc)
                    setCourseIds(JsonUtils.getJsonArray("courseIds", jsonDoc))
                }
            )
        }
        certificationDao.upsertAll(certifications)
    }

    private fun queueCourseResources(courseId: String?, stepId: String?, resources: JsonArray) {
        resources.forEach { resource ->
            pendingCourseResources.add(
                PendingCourseResource(resource.asJsonObject, courseId, stepId)
            )
        }
    }

    private fun extractCorrectChoices(questionJson: JsonObject): List<String> {
        val choices = JsonUtils.getJsonArray("choices", questionJson)
        fun resolveChoiceValue(raw: String): String {
            val matchedChoice = choices.firstOrNull {
                it.isJsonObject && JsonUtils.getString("id", it.asJsonObject) == raw
            }?.asJsonObject ?: return raw

            return ExamAnswerUtils.choiceDisplayValue(matchedChoice) ?: raw
        }

        val correctChoiceArray = JsonUtils.getJsonArray("correctChoice", questionJson)
        return if (!correctChoiceArray.isEmpty()) {
            correctChoiceArray.map { resolveChoiceValue(it.asString) }
        } else {
            val correctChoice = JsonUtils.getString("correctChoice", questionJson)
            if (correctChoice.isBlank()) emptyList() else listOf(resolveChoiceValue(correctChoice))
        }
    }

    override suspend fun flushPendingCourseResources() {
        val batch: List<PendingCourseResource>
        synchronized(pendingCourseResources) {
            if (pendingCourseResources.isEmpty()) return
            batch = ArrayList(pendingCourseResources)
            pendingCourseResources.clear()
        }

        val resourceIds = batch.map { JsonUtils.getString("_id", it.doc) }.filter { it.isNotBlank() }
        val existingMap = if (resourceIds.isNotEmpty()) {
            resourceIds.distinct()
                .chunked(300)
                .flatMap { myLibraryDao.getByIds(it) }
                .associateBy { it.id }
        } else {
            emptyMap()
        }

        val libraries = batch.mapNotNull { pending ->
            val resourceId = JsonUtils.getString("_id", pending.doc)
            val existing = existingMap[resourceId]
            MyLibrary.insertMyLibrary(
                MyLibrary.Companion.InsertParams(
                    doc = pending.doc,
                    spm = sharedPrefManager,
                    courseId = pending.courseId,
                    stepId = pending.stepId,
                    existing = existing
                )
            )
        }
        if (libraries.isNotEmpty()) {
            myLibraryDao.upsertAll(libraries)
            libraries.forEach { library ->
                if (library.mediaType == "HTML" && library.resourceLocalAddress.isNullOrBlank()) {
                    val resourceId = library.resourceId ?: return@forEach
                    try {
                        resourcesRepository.reconcileHtmlResourceOffline(resourceId)
                    } catch (e: Exception) {
                        Log.w("CoursesRepository", "reconcileHtmlResourceOffline failed for $resourceId", e)
                    }
                }
            }
        }
    }

    private suspend fun mapCourses(courses: List<MyCourse>): List<MyCourse> {
        if (courses.isEmpty()) return emptyList()
        val courseIds = courses.map { it.courseId ?: it.id }.distinct()
        val stepsByCourseId = if (courseIds.isEmpty()) {
            emptyMap()
        } else {
            courseStepDao.getByCourseIds(courseIds)
                .groupBy { it.courseId ?: "" }
        }
        return courses.map { course ->
            val courseKey = course.courseId ?: course.id
            course.apply { val steps = stepsByCourseId[courseKey].orEmpty(); courseSteps = steps.toMutableList(); setNumberOfSteps(steps.size) }
        }
    }

    private suspend fun mapCourse(course: MyCourse?): MyCourse? {
        if (course == null) return null
        val courseKey = course.courseId ?: course.id
        val steps = if (courseKey.isBlank()) {
            emptyList()
        } else {
            courseStepDao.getByCourseId(courseKey).map { it }
        }
        return course.apply { courseSteps = steps.toMutableList(); setNumberOfSteps(steps.size) }
    }

    private fun mergeUserIds(existingUserIds: List<String>?, newUserId: String?): List<String>? {
        val set = existingUserIds.orEmpty().filterTo(LinkedHashSet()) { it.isNotBlank() }
        if (!newUserId.isNullOrBlank()) {
            set.add(newUserId)
        }
        return set.toList().takeIf { it.isNotEmpty() }
    }
}
