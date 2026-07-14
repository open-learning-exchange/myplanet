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
import org.ole.planet.myplanet.di.LegacyRealmDispatcher
import org.ole.planet.myplanet.model.CourseProgressData
import org.ole.planet.myplanet.model.CourseStepData
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCertification
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmStepExam.Companion.insertCourseStepsExams
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.utils.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

class CoursesRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @LegacyRealmDispatcher legacyRealmDispatcher: CoroutineDispatcher,
    private val progressRepository: ProgressRepository,
    private val activitiesRepository: ActivitiesRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val tagsRepository: TagsRepository,
    private val ratingsRepository: RatingsRepository,
    private val sharedPrefManager: SharedPrefManager
) : RealmRepository(databaseService, legacyRealmDispatcher), CoursesRepository {

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
        return withRealm { realm ->
            val courses = realm.where(RealmMyCourse::class.java).`in`("courseId", courseIds.toTypedArray()).findAll()
            realm.copyFromRealm(courses)
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
        return queryList(RealmMyLibrary::class.java) {
            `in`("courseId", courseIds.toTypedArray())
            equalTo("resourceOffline", false)
            isNotNull("resourceLocalAddress")
        }
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
        val course = getCourseById(courseId)
        return course?.courseSteps?.toList() ?: emptyList()
    }

    override suspend fun markCoursesAdded(courseIds: List<String>, userId: String?): Result<Boolean> {
        return runCatching {
            if (courseIds.isEmpty()) {
                return@runCatching false
            }

            var courseFound = false
            executeTransaction { realm ->
                    val validCourseIds = courseIds.filter { it.isNotBlank() }
                    if (validCourseIds.isEmpty()) return@executeTransaction

                    val allFoundCourseIds = mutableListOf<String>()
                    val chunkSize = 1000
                    validCourseIds.chunked(chunkSize).forEach { chunk ->
                        val courses = realm.where(RealmMyCourse::class.java)
                            .`in`("courseId", chunk.toTypedArray())
                            .findAll()

                        if (courses.isNotEmpty()) {
                            courses.forEach { course ->
                                course.setUserId(userId)
                            }

                            allFoundCourseIds.addAll(courses.mapNotNull { it.courseId })
                            courseFound = true
                        }
                    }

                    if (!userId.isNullOrBlank() && allFoundCourseIds.isNotEmpty()) {
                        allFoundCourseIds.chunked(chunkSize).forEach { chunk ->
                            realm.where(RealmRemovedLog::class.java)
                                .equalTo("type", "courses")
                                .equalTo("userId", userId)
                                .`in`("docId", chunk.toTypedArray())
                                .findAll()
                                .deleteAllFromRealm()
                        }
                    }
                }

                courseFound
        }
    }

    private suspend fun getCourseResources(courseId: String?, isOffline: Boolean): List<RealmMyLibrary> {
        if (courseId.isNullOrEmpty()) {
            return emptyList()
        }
        return queryList(RealmMyLibrary::class.java) {
            equalTo("courseId", courseId)
            equalTo("resourceOffline", isOffline)
            isNotNull("resourceLocalAddress")
        }
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
        return withRealm { realm ->
            val queryObj = realm.where(RealmMyCourse::class.java)
            if (query.isEmpty()) {
                return@withRealm realm.copyFromRealm(queryObj.findAll())
            }

            val queryParts = query.split(" ").filterNot { it.isEmpty() }
            queryParts.forEach { part ->
                queryObj.contains("courseTitleNormal", Utilities.normalizeText(part), Case.INSENSITIVE)
            }
            val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }
            val data = queryObj.findAll()
            val normalizedQuery = Utilities.normalizeText(query)
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
            realm.copyFromRealm(startsWithQuery + containsQuery)
        }
    }

    override suspend fun filterCourses(
        searchText: String,
        gradeLevel: String,
        subjectLevel: String,
        tagNames: List<String>
    ): List<RealmMyCourse> {
        return withRealm { realm ->
            val courseIdsWithTags = if (tagNames.isNotEmpty()) {
                val matchingTagIds = realm.where(RealmTag::class.java)
                    .`in`("name", tagNames.toTypedArray())
                    .findAll()
                    .mapNotNull { it.id }
                realm.where(RealmTag::class.java)
                    .equalTo("db", "courses")
                    .`in`("tagId", matchingTagIds.toTypedArray())
                    .findAll()
                    .mapNotNull { it.linkId }
            } else {
                null
            }

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
        tags: List<RealmTag>,
        grade: String,
        subject: String
    ) {
        executeTransaction { realm ->
            val activity = realm.createObject(
                RealmSearchActivity::class.java,
                UUID.randomUUID().toString()
            )
            activity.user = userName
            activity.time = Calendar.getInstance().timeInMillis
            activity.createdOn = planetCode
            activity.parentCode = parentCode
            activity.text = searchText
            activity.type = "courses"
            val filter = JsonObject()

            filter.add("tags", RealmTag.getTagsArray(tags))
            filter.addProperty("doc.gradeLevel", grade)
            filter.addProperty("doc.subjectLevel", subject)
            activity.filter = JsonUtils.gson.toJson(filter)
        }
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

                    val removedLog = realm.where(RealmRemovedLog::class.java)
                        .equalTo("type", "courses")
                        .equalTo("userId", userId)
                        .equalTo("docId", courseId)
                        .findFirst()

                    removedLog?.deleteFromRealm()
                }
            }
        }
    }

    override suspend fun leaveCourse(courseId: String, userId: String): Result<Unit> {
        return runCatching {
            executeTransaction { realm ->
                val course = realm.where(RealmMyCourse::class.java)
                    .equalTo("courseId", courseId)
                    .findFirst()
                course?.removeUserId(userId)
                RealmRemovedLog.onRemove(realm, "courses", userId, courseId)
            }
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
                val query = realm.where(RealmStepExam::class.java)
                stepIds.chunked(1000).forEachIndexed { index, chunk ->
                    if (index > 0) query.or()
                    query.`in`("stepId", chunk.toTypedArray())
                }
                allExams.addAll(query.findAll())
            }
            val examsByStepId = allExams.groupBy { it.stepId }

            val examIds = allExams.mapNotNull { it.id }
            val questionsByExamId = if (examIds.isNotEmpty()) {
                val query = realm.where(RealmExamQuestion::class.java)
                examIds.chunked(1000).forEachIndexed { index, chunk ->
                    if (index > 0) query.or()
                    query.`in`("examId", chunk.toTypedArray())
                }
                val allQuestions = query.findAll()
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
                val query = realm.where(RealmAnswer::class.java)
                submissionIds.chunked(1000).forEachIndexed { index, chunk ->
                    if (index > 0) query.or()
                    query.`in`("submissionId", chunk.toTypedArray())
                }
                val allAnswers = query.findAll()
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
        return count(RealmCertification::class.java) {
            contains("courseIds", courseId)
        } > 0
    }

    override suspend fun updateCourseProgress(courseId: String?, stepNum: Int, passed: Boolean) {
        if (courseId.isNullOrEmpty()) return
        executeTransaction { realm ->
            val progress = realm.where(RealmCourseProgress::class.java)
                .equalTo("courseId", courseId)
                .equalTo("stepNum", stepNum)
                .findFirst()
            progress?.passed = passed
        }
    }

    override suspend fun getCourseStepData(stepId: String, userId: String?): CourseStepData {
        val step = findFirstCopy(RealmCourseStep::class.java) { equalTo("id", stepId) }
            ?: throw IllegalStateException("Step not found")
        val intermediate = withRealm { realm ->
            val resources = realm.where(RealmMyLibrary::class.java)
                .equalTo("stepId", stepId)
                .findAll()
                .let { realm.copyFromRealm(it) }
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

    override suspend fun getCourseTagsBulk(courseIds: List<String>): Map<String, List<RealmTag>> {
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
        RealmMyCourse.saveConcatenatedLinksToPrefs(sharedPrefManager)
    }
    override fun bulkInsertCertificationsFromSync(realm: Realm, jsonArray: JsonArray) {
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
            insertCertification(realm, jsonDoc)
        }
    }

    private fun insertCertification(realm: Realm, doc: JsonObject) {
        val id = JsonUtils.getString("_id", doc)
        var certification = realm.where(RealmCertification::class.java).equalTo("_id", id).findFirst()
        if (certification == null) {
            certification = realm.createObject(RealmCertification::class.java, id)
        }
        certification?.name = JsonUtils.getString("name", doc)
        certification?.setCourseIds(JsonUtils.getJsonArray("courseIds", doc))
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
            if (mRealm != null) {
                RealmMyLibrary.insertMyLibrary(RealmMyLibrary.Companion.InsertParams(doc = resource.asJsonObject, mRealm = mRealm, spm = spm, courseId = myCoursesID, stepId = stepId))
            }
        }
    }
}
