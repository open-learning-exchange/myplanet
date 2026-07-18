package org.ole.planet.myplanet.repository

import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Calendar
import java.util.HashMap
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.data.room.dao.CertificationDao
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.data.room.dao.TagDao
import org.ole.planet.myplanet.data.room.dao.legacy.AnswerDao
import org.ole.planet.myplanet.data.room.dao.legacy.CourseDao
import org.ole.planet.myplanet.data.room.dao.legacy.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.legacy.ExamDao
import org.ole.planet.myplanet.data.room.dao.legacy.QuestionDao
import org.ole.planet.myplanet.data.room.dao.legacy.SubmissionDao
import org.ole.planet.myplanet.data.room.entity.legacy.RoomCourseEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomCourseStepEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomExamEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomQuestionEntity
import org.ole.planet.myplanet.data.room.entity.legacy.toRealmModel
import org.ole.planet.myplanet.model.Certification
import org.ole.planet.myplanet.model.CourseProgressData
import org.ole.planet.myplanet.model.CourseStepData
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RemovedLog
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.utils.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

class CoursesRepositoryImpl @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val activitiesRepository: ActivitiesRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val tagsRepository: TagsRepository,
    private val ratingsRepository: RatingsRepository,
    private val sharedPrefManager: SharedPrefManager,
    private val certificationDao: CertificationDao,
    private val courseDao: CourseDao,
    private val courseStepDao: CourseStepDao,
    private val examDao: ExamDao,
    private val questionDao: QuestionDao,
    private val submissionDao: SubmissionDao,
    private val answerDao: AnswerDao,
    private val tagDao: TagDao,
    private val searchActivityDao: SearchActivityDao,
    private val courseProgressDao: CourseProgressDao,
    private val removedLogDao: RemovedLogDao,
    private val myLibraryDao: MyLibraryDao
) : CoursesRepository {

    private val pendingCourseResources =
        java.util.Collections.synchronizedList(mutableListOf<PendingCourseResource>())

    private data class PendingCourseResource(
        val doc: JsonObject,
        val courseId: String?,
        val stepId: String?
    )

    private data class ParsedCourseSyncPayload(
        val course: RoomCourseEntity,
        val steps: List<RoomCourseStepEntity>,
        val exams: List<RoomExamEntity>,
        val questions: List<RoomQuestionEntity>
    )

    override suspend fun getAllCourses(): List<RealmMyCourse> {
        return mapCourses(courseDao.getAll())
            .filter { !it.courseTitle.isNullOrEmpty() }
    }

    override fun getMyCourses(userId: String?, courses: List<RealmMyCourse>): List<RealmMyCourse> {
        if (userId == null) return emptyList()
        return courses.filter { it.userId?.contains(userId) == true }
    }

    override suspend fun getMyCourses(userId: String): List<RealmMyCourse> {
        return getMyCourses(userId, mapCourses(courseDao.getAll()))
    }

    override suspend fun getMyCoursesFlow(userId: String): Flow<List<RealmMyCourse>> {
        return courseDao.observeAll().map { courses ->
            mapCourses(courses).filter { it.userId?.contains(userId) == true }
        }
    }

    override suspend fun getCourseById(courseId: String): RealmMyCourse? {
        if (courseId.isBlank()) return null
        return mapCourse(courseDao.getByCourseId(courseId))
    }

    override fun getCourseByCourseIdFlow(courseId: String): Flow<RealmMyCourse?> {
        return courseDao.observeByCourseId(courseId).map { course ->
            mapCourse(course)
        }
    }

    override suspend fun getCoursesByIds(courseIds: List<String>): List<RealmMyCourse> {
        if (courseIds.isEmpty()) return emptyList()
        return mapCourses(courseDao.getByCourseIds(courseIds))
    }

    override suspend fun getCourseOnlineResources(courseId: String?): List<RealmMyLibrary> {
        return getCourseResources(courseId, isOffline = false)
    }

    override suspend fun getCourseOfflineResources(courseId: String?): List<RealmMyLibrary> {
        return getCourseResources(courseId, isOffline = true)
    }

    override suspend fun getCourseOfflineResources(courseIds: List<String>): List<RealmMyLibrary> {
        if (courseIds.isEmpty()) {
            return emptyList()
        }
        return myLibraryDao.getOfflineResourcesForCourses(courseIds)
    }

    override suspend fun getCourseExamCount(courseId: String?): Int {
        if (courseId.isNullOrEmpty()) {
            return 0
        }
        return examDao.getByCourseIdAndType(courseId, "courses").size
    }

    override suspend fun getCourseSteps(courseId: String): List<RealmCourseStep> {
        if (courseId.isBlank()) {
            return emptyList()
        }
        return courseStepDao.getByCourseId(courseId).map { it.toRealmModel() }
    }

    override suspend fun markCoursesAdded(courseIds: List<String>, userId: String?): Result<Boolean> {
        return runCatching {
            val validCourseIds = courseIds.filter { it.isNotBlank() }
            if (validCourseIds.isEmpty()) return@runCatching false

            val courses = courseDao.getByCourseIds(validCourseIds)
            if (courses.isEmpty()) {
                return@runCatching false
            }

            courseDao.upsertAll(
                courses.map { course ->
                    course.copy(userId = mergeUserIds(course.userId, userId))
                }
            )

            if (!userId.isNullOrBlank()) {
                validCourseIds.chunked(1000).forEach { chunk ->
                    removedLogDao.deleteByTypeUserAndDocs("courses", userId, chunk)
                }
            }

            true
        }
    }

    private suspend fun getCourseResources(courseId: String?, isOffline: Boolean): List<RealmMyLibrary> {
        if (courseId.isNullOrEmpty()) {
            return emptyList()
        }
        return myLibraryDao.getCourseResources(courseId, isOffline)
    }

    internal fun matchesAllParts(title: String, parts: List<String>): Boolean {
        for (part in parts) {
            if (!title.contains(part)) {
                return false
            }
        }
        return true
    }

    override suspend fun search(query: String): List<RealmMyCourse> {
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

        val startsWithQuery = mutableListOf<RealmMyCourse>()
        val containsQuery = mutableListOf<RealmMyCourse>()

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
    ): List<RealmMyCourse> {
        val courseIdsWithTags = if (tagNames.isNotEmpty()) {
            val matchingTagIds = tagDao.getByNames(tagNames).map { it.id }
            if (matchingTagIds.isEmpty()) {
                emptyList()
            } else {
                tagDao.getByDbAndTagIds("courses", matchingTagIds).mapNotNull { it.linkId }
            }
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
            .sortedBy { it.courseTitle?.lowercase() ?: "" }
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

            courseDao.getByCourseId(courseId)?.let { course ->
                courseDao.upsert(course.copy(userId = mergeUserIds(course.userId, userId)))
            }
            removedLogDao.deleteByTypeUserAndDoc("courses", userId, courseId)
        }
    }

    override suspend fun leaveCourse(courseId: String, userId: String): Result<Unit> {
        return runCatching {
            courseDao.getByCourseId(courseId)?.let { course ->
                val updatedUserIds = course.userId.orEmpty().toMutableList().apply { remove(userId) }
                courseDao.upsert(course.copy(userId = updatedUserIds))
            }
            removedLogDao.insert(RemovedLog().apply {
                id = UUID.randomUUID().toString()
                type = "courses"
                this.userId = userId
                docId = courseId
            })
            RealtimeSyncManager.getInstance().notifyTableUpdated(TableDataUpdate("courses", 0, 1))
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
        val stepIds = stepsList.mapNotNull { it.id }
        val allExams = if (stepIds.isEmpty()) emptyList() else examDao.getByStepIds(stepIds).map { it.toRealmModel() }
        val max = stepsList.size
        val title = courseTitle
        val examsByStepId = allExams.groupBy { it.stepId }

        val examIds = allExams.mapNotNull { it.id }
        val questionsByExamId = if (examIds.isEmpty()) {
            emptyMap()
        } else {
            questionDao.getByExamIds(examIds)
                .map { it.toRealmModel() }
                .groupBy { it.examId ?: "" }
                .filterKeys { it.isNotEmpty() }
        }

        val examIdsSet = examIds.toSet()
        val relevantSubmissions = submissionDao.getExamSubmissionsByUser(userId)
            .map { it.toRealmModel() }
            .filter { sub -> examIdsSet.contains(getParentBaseId(sub.parentId)) }

        val submissionsByExamId = relevantSubmissions.groupBy { sub ->
            getParentBaseId(sub.parentId).orEmpty()
        }.filterKeys { it.isNotEmpty() }

        val submissionIds = relevantSubmissions.mapNotNull { it.id }
        val answersBySubmissionId = if (submissionIds.isEmpty()) {
            emptyMap()
        } else {
            answerDao.getBySubmissionIds(submissionIds)
                .map { it.toRealmModel() }
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
        return CourseProgressData(title, current, max, array)
    }

    private fun getParentBaseId(parentId: String?): String? {
        return if (parentId?.contains("@") == true) parentId.split("@")[0] else parentId
    }

    private fun getExamObject(
        exams: Iterable<RealmStepExam>,
        ob: JsonObject,
        questionsByExamId: Map<String, List<RealmExamQuestion>>,
        submissionsByExamId: Map<String, List<RealmSubmission>>,
        answersBySubmissionId: Map<String, List<RealmAnswer>>
    ) {
        exams.forEach { exam ->
            exam.id?.let { examId ->
                val submissionsForExam = submissionsByExamId[examId] ?: emptyList()
                submissionsForExam.forEach { submission ->
                    val answers = submission.id?.let { answersBySubmissionId[it] } ?: emptyList()
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
        RealmMyCourse.saveConcatenatedLinksToPrefs(sharedPrefManager)
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
        val step = courseStepDao.getById(stepId)?.toRealmModel()
            ?: throw IllegalStateException("Step not found")
        val resources = myLibraryDao.getByStepId(stepId)
        val stepExams = examDao.getByStepIdAndType(stepId, "courses").map { it.toRealmModel() }
        val stepSurvey = examDao.getByStepIdAndType(stepId, "surveys").map { it.toRealmModel() }
        val intermediate = CourseStepData(step, resources, stepExams, stepSurvey, false)
        val userHasCourse = isMyCourse(userId, intermediate.step.courseId)
        return intermediate.copy(userHasCourse = userHasCourse)
    }

    override suspend fun getMyCourseIds(userId: String): JsonArray {
        val ids = JsonArray()
        getMyCourses(userId).mapNotNull { it.courseId }.forEach { ids.add(it) }
        return ids
    }

    override suspend fun removeCourseFromShelf(courseId: String, userId: String) {
        leaveCourse(courseId, userId)
    }

    override suspend fun logCourseVisit(courseId: String, title: String, userId: String) {
        activitiesRepository.logCourseVisit(courseId, title, userId)
    }

    override suspend fun getCurrentProgress(steps: List<RealmCourseStep?>?, userId: String?, courseId: String?): Int {
        return progressRepository.getCurrentProgress(steps, userId, courseId)
    }

    override suspend fun getCourseProgress(userId: String?, courseIds: List<String>): HashMap<String?, JsonObject> {
        return progressRepository.getCourseProgress(courseIds, userId)
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

    override suspend fun getCourseRatings(userId: String?): HashMap<String?, JsonObject> {
        return ratingsRepository.getCourseRatings(userId)
    }

    override suspend fun deleteCourseProgress(courseId: String?) {
        val examIds = courseId?.let { examDao.getByCourseId(it).map { exam -> exam.id } }.orEmpty()
        if (examIds.isNotEmpty()) {
            val submissions = submissionDao.getUnuploadedNonSurveyByParentIds(examIds.mapNotNull { it })
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
        RealmMyCourse.saveConcatenatedLinksToPrefs(sharedPrefManager)
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

        val courses = ArrayList<RoomCourseEntity>(documentList.size)
        val steps = ArrayList<RoomCourseStepEntity>()
        val exams = ArrayList<RoomExamEntity>()
        val questions = ArrayList<RoomQuestionEntity>()
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
        existingCourses: Map<String, RoomCourseEntity>
    ): ParsedCourseSyncPayload? {
        val courseId = JsonUtils.getString("_id", doc)
        if (courseId.isBlank()) return null

        val existingCourse = existingCourses[courseId]
        val title = JsonUtils.getString("courseTitle", doc)
        val description = JsonUtils.getString("description", doc)
        val baseUrl = UrlUtils.getUrl()
        extractLinks(description).forEach { link ->
            RealmMyCourse.addConcatenatedLink("$baseUrl/$link")
        }

        val stepIds = mutableListOf<String>()
        val parsedSteps = ArrayList<RoomCourseStepEntity>()
        val parsedExams = ArrayList<RoomExamEntity>()
        val parsedQuestions = ArrayList<RoomQuestionEntity>()
        val stepsJson = JsonUtils.getJsonArray("steps", doc)
        for (i in 0 until stepsJson.size()) {
            val stepElement = stepsJson[i]
            val stepId = Base64.encodeToString(stepElement.toString().toByteArray(), Base64.NO_WRAP)
            val stepJson = stepElement.asJsonObject
            val stepDescription = JsonUtils.getString("description", stepJson)
            extractLinks(stepDescription).forEach { link ->
                RealmMyCourse.addConcatenatedLink("$baseUrl/$link")
            }
            queueCourseResources(courseId, stepId, JsonUtils.getJsonArray("resources", stepJson))
            stepIds.add(stepId)
            parsedSteps.add(
                RoomCourseStepEntity(
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

        val course = RoomCourseEntity(
            id = existingCourse?.id ?: courseId,
            _id = courseId,
            _rev = JsonUtils.getString("_rev", doc),
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
            updatedDate = existingCourse?.updatedDate ?: 0,
            coverFileName = JsonUtils.getString("coverFileName", doc).ifEmpty { null },
            numberOfSteps = stepIds.size,
            steps = stepIds,
        )

        return ParsedCourseSyncPayload(course, parsedSteps, parsedExams, parsedQuestions)
    }

    private fun collectRoomExam(
        stepJson: JsonObject,
        examKey: String,
        courseId: String,
        stepId: String,
        exams: MutableList<RoomExamEntity>,
        questions: MutableList<RoomQuestionEntity>
    ) {
        if (!stepJson.has(examKey)) return
        val examJson = stepJson.getAsJsonObject(examKey)
        val examId = JsonUtils.getString("_id", examJson).ifBlank { "$courseId-$stepId-$examKey" }
        val questionArray = JsonUtils.getJsonArray("questions", examJson)
        exams.add(
            RoomExamEntity(
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
                RoomQuestionEntity(
                    id = questionId,
                    examId = examId,
                    type = JsonUtils.getString("type", questionJson),
                    header = JsonUtils.getString("title", questionJson),
                    question = JsonUtils.getString("body", questionJson).ifBlank { JsonUtils.getString("title", questionJson) },
                    choices = if (questionJson.has("choices")) {
                        JsonUtils.gson.toJson(JsonUtils.getJsonArray("choices", questionJson))
                    } else {
                        "[]"
                    },
                    correctChoice = extractCorrectChoices(questionJson),
                    grade = JsonUtils.getString("marks", questionJson).toIntOrNull() ?: 0,
                    order = i,
                    hasOtherOption = JsonUtils.getBoolean("hasOtherOption", questionJson),
                    scaleMax = JsonUtils.getInt("scaleMax", questionJson).let { if (it <= 0) 9 else it },
                    marks = JsonUtils.getString("marks", questionJson),
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
        val correctChoiceArray = JsonUtils.getJsonArray("correctChoice", questionJson)
        if (correctChoiceArray.size() > 0) {
            return correctChoiceArray.map { it.asString }
        }

        val correctChoice = JsonUtils.getString("correctChoice", questionJson)
        if (correctChoice.isBlank()) {
            return emptyList()
        }

        val choices = JsonUtils.getJsonArray("choices", questionJson)
        return choices.mapNotNull { choiceElement ->
            val choice = choiceElement.asJsonObject
            if (JsonUtils.getString("id", choice) == correctChoice) {
                JsonUtils.getString("res", choice).ifBlank { null }
            } else {
                null
            }
        }
    }

    override suspend fun flushPendingCourseResources() {
        val batch: List<PendingCourseResource>
        synchronized(pendingCourseResources) {
            if (pendingCourseResources.isEmpty()) return
            batch = ArrayList(pendingCourseResources)
            pendingCourseResources.clear()
        }
        val libraries = batch.mapNotNull { pending ->
            val resourceId = JsonUtils.getString("_id", pending.doc)
            val existing = myLibraryDao.getById(resourceId)
            RealmMyLibrary.insertMyLibrary(
                RealmMyLibrary.Companion.InsertParams(
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
        }
    }

    private suspend fun mapCourses(courses: List<RoomCourseEntity>): List<RealmMyCourse> {
        if (courses.isEmpty()) return emptyList()
        val courseIds = courses.mapNotNull { it.courseId ?: it.id }.distinct()
        val stepsByCourseId = if (courseIds.isEmpty()) {
            emptyMap()
        } else {
            courseStepDao.getByCourseIds(courseIds)
                .groupBy { it.courseId ?: "" }
                .mapValues { entry -> entry.value.map { it.toRealmModel() } }
        }
        return courses.map { course ->
            val courseKey = course.courseId ?: course.id
            course.toRealmModel(stepsByCourseId[courseKey].orEmpty())
        }
    }

    private suspend fun mapCourse(course: RoomCourseEntity?): RealmMyCourse? {
        if (course == null) return null
        val courseKey = course.courseId ?: course.id
        val steps = if (courseKey.isBlank()) {
            emptyList()
        } else {
            courseStepDao.getByCourseId(courseKey).map { it.toRealmModel() }
        }
        return course.toRealmModel(steps)
    }

    private fun mergeUserIds(existingUserIds: List<String>?, newUserId: String?): List<String>? {
        val merged = existingUserIds.orEmpty().toMutableList()
        if (!newUserId.isNullOrBlank() && !merged.contains(newUserId)) {
            merged.add(newUserId)
        }
        return merged.takeIf { it.isNotEmpty() }
    }
}
