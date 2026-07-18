package org.ole.planet.myplanet.repository

import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Case
import io.realm.Realm
import io.realm.RealmList
import java.util.Calendar
import java.util.HashMap
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.CourseProgressData
import org.ole.planet.myplanet.model.CourseStepData
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.data.room.dao.CertificationDao
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.legacy.CourseDao
import org.ole.planet.myplanet.data.room.dao.legacy.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.legacy.ExamDao
import org.ole.planet.myplanet.data.room.dao.legacy.QuestionDao
import org.ole.planet.myplanet.data.room.entity.legacy.RoomCourseEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomCourseStepEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomExamEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomQuestionEntity
import org.ole.planet.myplanet.data.room.entity.legacy.toRealmModel
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.data.room.dao.TagDao
import org.ole.planet.myplanet.model.Certification
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RemovedLog
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmStepExam.Companion.insertCourseStepsExams
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.utils.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

class CoursesRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
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
    private val tagDao: TagDao,
    private val searchActivityDao: SearchActivityDao,
    private val courseProgressDao: CourseProgressDao,
    private val removedLogDao: RemovedLogDao,
    private val myLibraryDao: MyLibraryDao
) : RealmRepository(databaseService, realmDispatcher), CoursesRepository {

    // Resources embedded in synced course steps are collected during the (Realm) course-insert
    // transaction and flushed to the Room library table afterwards (DAO calls can't nest in a
    // Realm transaction). See flushPendingCourseResources().
    private val pendingCourseResources =
        java.util.Collections.synchronizedList(mutableListOf<PendingCourseResource>())

    private data class PendingCourseResource(
        val doc: JsonObject,
        val courseId: String?,
        val stepId: String?
    )

    override suspend fun getAllCourses(): List<RealmMyCourse> {
        return queryList(RealmMyCourse::class.java, maxDepth = 0) {
            isNotEmpty("courseTitle")
        }
    }

    override fun getMyCourses(userId: String?, courses: List<RealmMyCourse>): List<RealmMyCourse> {
        if (userId == null) return emptyList()
        return courses.filter { it.userId?.contains(userId) == true }
    }

    override suspend fun getMyCourses(userId: String): List<RealmMyCourse> {
        return queryList(RealmMyCourse::class.java) {
            equalTo("userId", userId)
        }
    }

    override suspend fun getMyCoursesFlow(userId: String): Flow<List<RealmMyCourse>> {
        return queryListFlow(RealmMyCourse::class.java) {
            equalTo("userId", userId)
        }
    }

    override suspend fun getCourseById(courseId: String): RealmMyCourse? {
        if (courseId.isBlank()) return null
        return findByField(RealmMyCourse::class.java, "courseId", courseId)
    }

    override fun getCourseByCourseIdFlow(courseId: String): Flow<RealmMyCourse?> {
        return queryListFlow(RealmMyCourse::class.java) {
            equalTo("courseId", courseId)
        }.map { it.firstOrNull() }
    }

    override suspend fun getCoursesByIds(courseIds: List<String>): List<RealmMyCourse> {
        if (courseIds.isEmpty()) return emptyList()
        return queryList(RealmMyCourse::class.java) {
            `in`("courseId", courseIds.toTypedArray())
        }
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
        return count(RealmStepExam::class.java) {
            equalTo("courseId", courseId)
        }.toInt()
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

            var courseFound = false
            executeTransaction { realm ->
                validCourseIds.chunked(1000).forEach { chunk ->
                    val courses = realm.where(RealmMyCourse::class.java)
                        .`in`("courseId", chunk.toTypedArray())
                        .findAll()

                    if (courses.isNotEmpty()) {
                        courses.forEach { course -> course.setUserId(userId) }
                        courseFound = true
                    }
                }
            }

            if (!userId.isNullOrBlank() && courseFound) {
                validCourseIds.chunked(1000).forEach { chunk ->
                    removedLogDao.deleteByTypeUserAndDocs("courses", userId, chunk)
                }
            }

            courseFound
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
        if (query.isEmpty()) {
            return queryList(RealmMyCourse::class.java)
        }

        val queryParts = query.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(query)

        val data = queryList(RealmMyCourse::class.java) {
            queryParts.forEach { part ->
                contains("courseTitleNormal", Utilities.normalizeText(part), Case.INSENSITIVE)
            }
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

        return withRealm { realm ->
            var query = realm.where(RealmMyCourse::class.java)
            if (searchText.isNotEmpty()) {
                query = query.contains("courseTitle", searchText, io.realm.Case.INSENSITIVE)
            }
            if (gradeLevel.isNotEmpty()) {
                query = query.equalTo("gradeLevel", gradeLevel)
            }
            if (subjectLevel.isNotEmpty()) {
                query = query.equalTo("subjectLevel", subjectLevel)
            }
            courseIdsWithTags?.let {
                query = query.`in`("courseId", it.toTypedArray())
            }
            query = query.isNotEmpty("courseTitle")

            val results = query.sort("courseTitle", io.realm.Sort.ASCENDING).findAll()
            realm.copyFromRealm(results, 0)
        }
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

            executeTransaction { realm ->
                val course = realm.where(RealmMyCourse::class.java)
                    .equalTo("courseId", courseId)
                    .findFirst()

                course?.let {
                    if (it.userId?.contains(userId) == false) {
                        it.setUserId(userId)
                    }

                }
            }
            removedLogDao.deleteByTypeUserAndDoc("courses", userId, courseId)
        }
    }

    override suspend fun leaveCourse(courseId: String, userId: String): Result<Unit> {
        return runCatching {
            executeTransaction { realm ->
                val course = realm.where(RealmMyCourse::class.java)
                    .equalTo("courseId", courseId)
                    .findFirst()
                course?.removeUserId(userId)
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
        return queryList(RealmMyCourse::class.java) {
            equalTo("courseId", courseId)
            equalTo("userId", userId)
        }.isNotEmpty()
    }

    override suspend fun getCourseProgress(courseId: String, userId: String?): CourseProgressData {
        val stepsList = getCourseSteps(courseId)
        val current = progressRepository.getCurrentProgress(stepsList, userId, courseId)
        val courseTitle = getCourseById(courseId)?.courseTitle
        return withRealm { realm ->
            val max = stepsList.size
            val title = courseTitle

            val stepIds = stepsList.mapNotNull { it.id }
            val allExams = mutableListOf<RealmStepExam>()
            if (stepIds.isNotEmpty()) {
                stepIds.chunked(1000).forEach { chunk ->
                    val chunkExams = realm.where(RealmStepExam::class.java)
                        .`in`("stepId", chunk.toTypedArray())
                        .findAll()
                    allExams.addAll(chunkExams)
                }
            }
            val examsByStepId = allExams.groupBy { it.stepId }

            val examIds = allExams.mapNotNull { it.id }
            val questionsByExamId = if (examIds.isNotEmpty()) {
                val allQuestions = mutableListOf<RealmExamQuestion>()
                examIds.chunked(1000).forEach { chunk ->
                    val chunkQuestions = realm.where(RealmExamQuestion::class.java)
                        .`in`("examId", chunk.toTypedArray())
                        .findAll()
                    allQuestions.addAll(chunkQuestions)
                }
                allQuestions.groupBy { it.examId ?: "" }
                    .filterKeys { it.isNotEmpty() }
            } else {
                emptyMap()
            }

            // To eliminate N+1 queries, we fetch all relevant submissions for the user upfront.
            // We fetch all 'exam' submissions for the user and filter in memory to handle legacy formats
            // and avoid doubling the query size with multiple ID variants.
            val examIdsSet = examIds.toSet()
            val userSubmissions = realm.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .equalTo("type", "exam")
                .findAll()

            val relevantSubmissions = userSubmissions.filter { sub ->
                val pId = sub.parentId
                val basePId = if (pId?.contains("@") == true) pId.split("@")[0] else pId
                examIdsSet.contains(basePId)
            }

            val submissionsByExamId = relevantSubmissions.groupBy { sub ->
                val pId = sub.parentId
                if (pId?.contains("@") == true) pId.split("@")[0] else pId ?: ""
            }.filterKeys { it.isNotEmpty() }

            val submissionIds = relevantSubmissions.mapNotNull { it.id }
            val answersBySubmissionId = if (submissionIds.isNotEmpty()) {
                // Realm IN query limit is around 1000 items, so we chunk the list to avoid query length limits.
                val allAnswers = mutableListOf<RealmAnswer>()
                submissionIds.chunked(1000).forEach { chunk ->
                    val chunkAnswers = realm.where(RealmAnswer::class.java)
                        .`in`("submissionId", chunk.toTypedArray())
                        .findAll()
                    allAnswers.addAll(chunkAnswers)
                }
                allAnswers.groupBy { it.submissionId ?: "" }
                    .filterKeys { it.isNotEmpty() }
            } else {
                emptyMap()
            }

            val array = JsonArray()
            stepsList.forEach { step ->
                val ob = JsonObject()
                ob.addProperty("stepId", step.id)
                val exams = examsByStepId[step.id] ?: emptyList()
                getExamObject(exams, ob, questionsByExamId, submissionsByExamId, answersBySubmissionId)
                array.add(ob)
            }
            CourseProgressData(title, current, max, array)
        }
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
        var processedCount = 0
        try {
            withRealm { realm ->
                realm.executeTransaction { realmTx ->
                    documents.forEach { doc ->
                        try {
                            insertMyCourse(shelfId ?: "", doc, realmTx, sharedPrefManager)
                            processedCount++
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        val intermediate = withRealm { realm ->
            val stepExams = realm.where(RealmStepExam::class.java)
                .equalTo("stepId", stepId)
                .equalTo("type", "courses")
                .findAll()
                .let { realm.copyFromRealm(it) }
            val stepSurvey = realm.where(RealmStepExam::class.java)
                .equalTo("stepId", stepId)
                .equalTo("type", "surveys")
                .findAll()
                .let { realm.copyFromRealm(it) }
            CourseStepData(step, resources, stepExams, stepSurvey, false)
        }
        val userHasCourse = isMyCourse(userId, intermediate.step.courseId)
        return intermediate.copy(userHasCourse = userHasCourse)
    }

    override suspend fun getMyCourseIds(userId: String): JsonArray {
        return withRealm { realm ->
            val myCourses = realm.where(RealmMyCourse::class.java)
                .equalTo("userId", userId)
                .findAll()

            val ids = JsonArray()
            myCourses.asSequence().mapNotNull { it.courseId }.forEach { ids.add(it) }
            ids
        }
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
        executeTransaction { realm ->
            val examList = realm.where(RealmStepExam::class.java).equalTo("courseId", courseId).findAll()
            val examIds = examList.mapNotNull { it.id }.toTypedArray()
            if (examIds.isNotEmpty()) {
                realm.where(RealmSubmission::class.java)
                    .`in`("parentId", examIds)
                    .notEqualTo("type", "survey")
                    .equalTo("uploaded", false)
                    .findAll()
                    .deleteAllFromRealm()
            }
        }
    }

    override fun bulkInsertFromSync(realm: Realm, jsonArray: JsonArray) {
        val documentList = ArrayList<JsonObject>(jsonArray.size())
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        documentList.forEach { jsonDoc ->
            val startedTransaction = !realm.isInTransaction
            if (startedTransaction) {
                realm.beginTransaction()
            }
            try {
                insertMyCourse("", jsonDoc, realm, sharedPrefManager)
                if (startedTransaction) {
                    realm.commitTransaction()
                }
            } catch (e: Exception) {
                if (startedTransaction && realm.isInTransaction) {
                    realm.cancelTransaction()
                }
                throw e
            }
        }
        upsertRoomCoursesFromSync(documentList)
        RealmMyCourse.saveConcatenatedLinksToPrefs(sharedPrefManager)
    }

    private fun upsertRoomCoursesFromSync(documentList: List<JsonObject>) {
        val courses = ArrayList<RoomCourseEntity>(documentList.size)
        val steps = ArrayList<RoomCourseStepEntity>()
        val exams = ArrayList<RoomExamEntity>()
        val questions = ArrayList<RoomQuestionEntity>()

        documentList.forEach { doc ->
            val courseId = JsonUtils.getString("_id", doc)
            if (courseId.isBlank()) return@forEach
            val stepIds = mutableListOf<String>()
            val stepsJson = JsonUtils.getJsonArray("steps", doc)
            for (i in 0 until stepsJson.size()) {
                val stepElement = stepsJson[i]
                val stepId = Base64.encodeToString(stepElement.toString().toByteArray(), Base64.NO_WRAP)
                val stepJson = stepElement.asJsonObject
                stepIds.add(stepId)
                steps.add(
                    RoomCourseStepEntity(
                        id = stepId,
                        courseId = courseId,
                        stepTitle = JsonUtils.getString("stepTitle", stepJson),
                        description = JsonUtils.getString("description", stepJson),
                        noOfResources = JsonUtils.getJsonArray("resources", stepJson).size(),
                    )
                )
                collectRoomExam(stepJson, "exam", courseId, stepId, exams, questions)
                collectRoomExam(stepJson, "survey", courseId, stepId, exams, questions)
            }
            courses.add(
                RoomCourseEntity(
                    id = courseId,
                    _id = courseId,
                    _rev = JsonUtils.getString("_rev", doc),
                    courseId = courseId,
                    courseTitle = JsonUtils.getString("courseTitle", doc),
                    description = JsonUtils.getString("description", doc),
                    createdDate = JsonUtils.getLong("createdDate", doc),
                    steps = stepIds,
                )
            )
        }

        if (courses.isEmpty() && steps.isEmpty() && exams.isEmpty() && questions.isEmpty()) return

        databaseService.room.runInTransaction {
            if (courses.isNotEmpty()) courseDao.upsertAllBlocking(courses)
            if (steps.isNotEmpty()) courseStepDao.upsertAllBlocking(steps)
            if (exams.isNotEmpty()) examDao.upsertAllBlocking(exams)
            if (questions.isNotEmpty()) questionDao.upsertAllBlocking(questions)
        }
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
                    question = JsonUtils.getString("body", questionJson).ifBlank { JsonUtils.getString("title", questionJson) },
                    choices = JsonUtils.getJsonArray("choices", questionJson).map { it.toString() },
                    correctChoice = JsonUtils.getJsonArray("correctChoice", questionJson).map { it.asString },
                    grade = JsonUtils.getString("marks", questionJson).toIntOrNull() ?: 0,
                    order = i,
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

    private fun insertMyCourse(shelfId: String, doc: JsonObject, realmTx: Realm, spm: SharedPrefManager) {
        val id = JsonUtils.getString("_id", doc)
        var myMyCoursesDB = realmTx.where(RealmMyCourse::class.java).equalTo("id", id).findFirst()
        if (myMyCoursesDB == null) {
            myMyCoursesDB = realmTx.createObject(RealmMyCourse::class.java, id)
        }
        myMyCoursesDB?.setUserId(shelfId)
        myMyCoursesDB?.courseId = JsonUtils.getString("_id", doc)
        myMyCoursesDB?.courseRev = JsonUtils.getString("_rev", doc)
        myMyCoursesDB?.languageOfInstruction = JsonUtils.getString("languageOfInstruction", doc)
        val title = JsonUtils.getString("courseTitle", doc)
        myMyCoursesDB?.courseTitle = title
        myMyCoursesDB?.courseTitleNormal = title.let { Utilities.normalizeText(it) }
        myMyCoursesDB?.memberLimit = JsonUtils.getInt("memberLimit", doc)
        val description = JsonUtils.getString("description", doc)
        myMyCoursesDB?.description = description
        val links = extractLinks(description)
        val baseUrl = UrlUtils.getUrl()
        for (link in links) {
            RealmMyCourse.addConcatenatedLink("$baseUrl/$link")
        }
        myMyCoursesDB?.method = JsonUtils.getString("method", doc)
        myMyCoursesDB?.gradeLevel = JsonUtils.getString("gradeLevel", doc)
        myMyCoursesDB?.subjectLevel = JsonUtils.getString("subjectLevel", doc)
        myMyCoursesDB?.createdDate = JsonUtils.getLong("createdDate", doc)
        myMyCoursesDB?.coverFileName = JsonUtils.getString("coverFileName", doc).ifEmpty { null }
        val courseStepsJsonArray = JsonUtils.getJsonArray("steps", doc)
        val stepsSize = courseStepsJsonArray.size()
        myMyCoursesDB?.setNumberOfSteps(stepsSize)
        val courseStepsList = mutableListOf<RealmCourseStep>()
        val examCache = HashMap<String, RealmStepExam>()
        val examIds = mutableListOf<String>()
        for (i in 0 until stepsSize) {
            val stepJson = courseStepsJsonArray[i].asJsonObject
            if (stepJson.has("exam")) {
                val id = JsonUtils.getString("_id", stepJson.getAsJsonObject("exam"))
                if (id.isNotEmpty()) examIds.add(id)
            }
            if (stepJson.has("survey")) {
                val id = JsonUtils.getString("_id", stepJson.getAsJsonObject("survey"))
                if (id.isNotEmpty()) examIds.add(id)
            }
        }
        examIds.chunked(900).forEach { chunk ->
            if (chunk.isNotEmpty()) {
                realmTx.where(RealmStepExam::class.java).`in`("id", chunk.toTypedArray()).findAll().forEach {
                    it.id?.let { id -> examCache[id] = it }
                }
            }
        }

        for (i in 0 until stepsSize) {
            val stepElement = courseStepsJsonArray[i]
            val stepId = Base64.encodeToString(stepElement.toString().toByteArray(), Base64.NO_WRAP)
            val stepJson = stepElement.asJsonObject
            val step = RealmCourseStep()
            step.id = stepId
            step.stepTitle = JsonUtils.getString("stepTitle", stepJson)
            val stepDescription = JsonUtils.getString("description", stepJson)
            step.description = stepDescription
            val stepLinks = extractLinks(stepDescription)
            for (stepLink in stepLinks) {
                RealmMyCourse.addConcatenatedLink("$baseUrl/$stepLink")
            }
            insertCourseStepsAttachments(myMyCoursesDB?.courseId, stepId, JsonUtils.getJsonArray("resources", stepJson), realmTx, spm)
            insertExam(stepJson, realmTx, stepId, i + 1, myMyCoursesDB, examCache)
            insertSurvey(stepJson, realmTx, stepId, i + 1, myMyCoursesDB, examCache)
            step.noOfResources = JsonUtils.getJsonArray("resources", stepJson).size()
            step.courseId = myMyCoursesDB?.courseId
            courseStepsList.add(step)
        }
        myMyCoursesDB?.courseSteps = RealmList()
        myMyCoursesDB?.courseSteps?.addAll(courseStepsList)
    }

    private fun insertExam(stepContainer: JsonObject, mRealm: Realm, stepId: String, i: Int, course: RealmMyCourse?, examCache: HashMap<String, RealmStepExam>? = null) {
        if (stepContainer.has("exam")) {
            val obj = stepContainer.getAsJsonObject("exam")
            obj.addProperty("stepNumber", i)
            insertCourseStepsExams(course?.courseId, stepId, obj, "", mRealm, examCache)
        }
    }

    private fun insertSurvey(stepContainer: JsonObject, mRealm: Realm, stepId: String, i: Int, course: RealmMyCourse?, examCache: HashMap<String, RealmStepExam>? = null) {
        if (stepContainer.has("survey")) {
            val obj = stepContainer.getAsJsonObject("survey")
            obj.addProperty("stepNumber", i)
            obj.addProperty("createdDate", course?.createdDate)
            insertCourseStepsExams(course?.courseId, stepId, obj, "", mRealm, examCache)
        }
    }

    private fun insertCourseStepsAttachments(myCoursesID: String?, stepId: String?, resources: JsonArray, mRealm: Realm?, spm: SharedPrefManager) {
        resources.forEach { resource ->
            // Resources live in Room now; queue them and flush after this Realm transaction.
            pendingCourseResources.add(
                PendingCourseResource(resource.asJsonObject, myCoursesID, stepId)
            )
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
}
