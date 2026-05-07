package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.CourseStepData
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCertification
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRemovedLog
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.utils.JsonUtils

class CoursesRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val progressRepository: ProgressRepository,
    private val activitiesRepository: ActivitiesRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val tagsRepository: TagsRepository,
    private val ratingsRepository: RatingsRepository,
    private val sharedPrefManager: org.ole.planet.myplanet.services.SharedPrefManager
) : RealmRepository(databaseService, realmDispatcher), CoursesRepository {

    override suspend fun getAllCourses(): List<RealmMyCourse> {
        return queryList(RealmMyCourse::class.java) {
            isNotEmpty("courseTitle")
        }
    }

    override suspend fun getAllCourses(orderBy: String, sort: io.realm.Sort): List<RealmMyCourse> {
        return withRealm { realm ->
            val results = realm.where(RealmMyCourse::class.java)
                .isNotEmpty("courseTitle")
                .sort(orderBy, sort)
                .findAll()
            realm.copyFromRealm(results)
        }
    }

    override fun getAllCourses(userId: String?, libs: List<RealmMyCourse>): List<RealmMyCourse> {
        return libs.onEach { it.isMyCourse = it.userId?.contains(userId) == true }
    }

    override fun getMyCourseByUserId(userId: String?, libs: List<RealmMyCourse>?): List<RealmMyCourse> {
        return libs?.filter { it.userId?.contains(userId) == true } ?: emptyList()
    }

    override fun getOurCourse(userId: String?, libs: List<RealmMyCourse>): List<RealmMyCourse> {
        return libs.filter { it.userId?.contains(userId) != true }
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
        return withRealm { realm ->
            realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", courseId)
                .findFirst()?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getCourseByCourseId(courseId: String): RealmMyCourse? {
        if (courseId.isBlank()) {
            return null
        }
        return withRealm { realm ->
            val course = realm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
            course?.let { realm.copyFromRealm(it) }
        }
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
        return withRealm { realm ->
            val course = realm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
            val steps = course?.courseSteps
            if (steps != null) java.util.Collections.unmodifiableList(realm.copyFromRealm(steps)) else emptyList()
        }
    }

    override suspend fun getCourseStepIds(courseId: String): List<String?> {
        if (courseId.isBlank()) {
            return emptyList()
        }
        return withRealm { realm ->
            val course = realm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
            course?.courseSteps?.map { it.id } ?: emptyList()
        }
    }

    override suspend fun markCourseAdded(courseId: String, userId: String?): Result<Boolean> {
        if (courseId.isBlank()) {
            return Result.success(false)
        }
        return markCoursesAdded(listOf(courseId), userId)
    }

    override suspend fun markCoursesAdded(courseIds: List<String>, userId: String?): Result<Boolean> {
        return withContext(databaseService.ioDispatcher) {
            runCatching {
                if (courseIds.isEmpty()) {
                    return@runCatching false
                }

                var courseFound = false
                executeTransaction { realm ->
                    val validCourseIds = courseIds.filter { it.isNotBlank() }
                    if (validCourseIds.isEmpty()) return@executeTransaction

                    val chunkSize = 1000
                    validCourseIds.chunked(chunkSize).forEach { chunk ->
                        val courses = realm.where(RealmMyCourse::class.java)
                            .`in`("courseId", chunk.toTypedArray())
                            .findAll()

                        if (courses.isNotEmpty()) {
                            courses.forEach { course ->
                                course.setUserId(userId)
                            }

                            val foundCourseIds = courses.mapNotNull { it.courseId }.toTypedArray()
                            if (!userId.isNullOrBlank() && foundCourseIds.isNotEmpty()) {
                                realm.where(RealmRemovedLog::class.java)
                                    .equalTo("type", "courses")
                                    .equalTo("userId", userId)
                                    .`in`("docId", foundCourseIds)
                                    .findAll()
                                    .deleteAllFromRealm()
                            }
                            courseFound = true
                        }
                    }
                }

                courseFound
            }
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

    private fun normalizeText(str: String): String {
        val lowercased = str.lowercase(Locale.getDefault())
        val normalized = Normalizer.normalize(lowercased, Normalizer.Form.NFD)
        val sb = StringBuilder(normalized.length)
        for (i in 0 until normalized.length) {
            val c = normalized[i]
            // NON_SPACING_MARK matches Unicode category Mn (Combining Diacritical Marks)
            if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun matchesAllParts(title: String, parts: List<String>): Boolean {
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
            val normalizedQueryParts = queryParts.map { normalizeText(it) }
            val data = queryObj.findAll()
            val normalizedQuery = normalizeText(query)
            val startsWithQuery = mutableListOf<RealmMyCourse>()
            val containsQuery = mutableListOf<RealmMyCourse>()

            for (item in data) {
                val title = item.courseTitle?.let { normalizeText(it) } ?: continue

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

            val results = query.findAll()
            val sortedList = results
                .filter { !it.courseTitle.isNullOrBlank() }
                .sortedWith(compareBy({ it.isMyCourse }, { it.courseTitle }))
            realm.copyFromRealm(sortedList)
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
            val filter = com.google.gson.JsonObject()

            filter.add("tags", RealmTag.getTagsArray(tags))
            filter.addProperty("doc.gradeLevel", grade)
            filter.addProperty("doc.subjectLevel", subject)
            activity.filter = JsonUtils.gson.toJson(filter)
        }
    }

    override suspend fun joinCourse(courseId: String, userId: String): Result<Unit> {
        return withContext(databaseService.ioDispatcher) {
            runCatching {
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
    }

    override suspend fun leaveCourse(courseId: String, userId: String): Result<Unit> {
        return withContext(databaseService.ioDispatcher) {
            runCatching {
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

    override suspend fun getCourseProgress(courseId: String, userId: String?): org.ole.planet.myplanet.model.CourseProgressData {
        val stepsList = getCourseSteps(courseId)
        val current = progressRepository.getCurrentProgress(stepsList, userId, courseId)
        return withRealm { realm ->
            val max = stepsList.size
            val course = realm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
            val title = course?.courseTitle

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
                val ob = com.google.gson.JsonObject()
                ob.addProperty("stepId", step.id)
                val exams = examsByStepId[step.id] ?: emptyList()
                getExamObject(exams, ob, questionsByExamId, submissionsByExamId, answersBySubmissionId)
                array.add(ob)
            }
            org.ole.planet.myplanet.model.CourseProgressData(title, current, max, array)
        }
    }

    private fun getExamObject(
        exams: Iterable<RealmStepExam>,
        ob: com.google.gson.JsonObject,
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
                            RealmMyCourse.insertMyCourses(shelfId, doc, realmTx, sharedPrefManager)
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
        return withRealm { realm ->
            realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", courseId)
                .findFirst()?.courseTitle
        }
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
        val intermediate = withRealm { realm ->
            val step = realm.where(RealmCourseStep::class.java)
                .equalTo("id", stepId)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
                ?: throw IllegalStateException("Step not found")
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

    override suspend fun getCourseProgress(userId: String?): java.util.HashMap<String?, com.google.gson.JsonObject> {
        return progressRepository.getCourseProgress(userId)
    }

    override suspend fun isStepCompleted(stepId: String?, userId: String?): Boolean {
        return submissionsRepository.isStepCompleted(stepId, userId)
    }

    override suspend fun hasUnfinishedSurveys(courseId: String, userId: String?): Boolean {
        return submissionsRepository.hasUnfinishedSurveys(courseId, userId)
    }

    override suspend fun getCourseTags(courseId: String): List<RealmTag> {
        return tagsRepository.getTagsForCourse(courseId)
    }

    override suspend fun getCourseTagsBulk(courseIds: List<String>): Map<String, List<RealmTag>> {
        return tagsRepository.getTagsForCourses(courseIds)
    }

    override suspend fun getCourseRatings(userId: String?): HashMap<String?, com.google.gson.JsonObject> {
        return ratingsRepository.getCourseRatings(userId)
    }

    override suspend fun deleteCourseProgress(courseId: String?) {
        executeTransaction { realm ->
            realm.where(RealmCourseProgress::class.java).equalTo("courseId", courseId).findAll().deleteAllFromRealm()
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

    override suspend fun filterCoursesByTag(
        query: String,
        tags: List<RealmTag>,
        isMyCourseLib: Boolean,
        userId: String?
    ): List<RealmMyCourse> {
        return withRealm { realm ->
            var realmQuery = realm.where(RealmMyCourse::class.java)

            if (tags.isNotEmpty()) {
                val tagIds = tags.mapNotNull { it.id }.toTypedArray()
                val linkedCourseIds = realm.where(RealmTag::class.java)
                    .equalTo("db", "courses")
                    .`in`("tagId", tagIds)
                    .findAll()
                    .mapNotNull { it.linkId }
                    .toTypedArray()

                if (linkedCourseIds.isEmpty()) {
                    return@withRealm emptyList()
                }
                realmQuery = realmQuery.`in`("courseId", linkedCourseIds)
            }

            if (isMyCourseLib && !userId.isNullOrBlank()) {
                realmQuery = realmQuery.equalTo("userId", userId)
            }

            val data = realmQuery.findAll()

            val list: List<RealmMyCourse> = if (query.isEmpty()) {
                realm.copyFromRealm(data)
            } else {
                val queryParts = query.split(" ").filterNot { it.isEmpty() }
                val normalizedQueryParts = queryParts.map { normalizeText(it) }
                val normalizedQuery = normalizeText(query)
                val startsWithQuery = mutableListOf<RealmMyCourse>()
                val containsQuery = mutableListOf<RealmMyCourse>()

                for (item in data) {
                    val title = item.courseTitle?.let { normalizeText(it) } ?: continue

                    if (title.startsWith(normalizedQuery)) {
                        startsWithQuery.add(item)
                    } else if (matchesAllParts(title, normalizedQueryParts)) {
                        containsQuery.add(item)
                    }
                }
                val filteredData = startsWithQuery + containsQuery
                realm.copyFromRealm(filteredData)
            }

            if (!isMyCourseLib) {
                list.forEach { it.isMyCourse = it.userId?.contains(userId) == true }
            }

            list.distinctBy { it.courseId }
        }
    }

    override fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: JsonArray) {
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
            RealmMyCourse.insert(realm, jsonDoc, sharedPrefManager)
        }
    }
    override fun bulkInsertCertificationsFromSync(realm: io.realm.Realm, jsonArray: JsonArray) {
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
            insertCertification(realm, jsonDoc)
        }
    }

    override fun insertCertification(realm: io.realm.Realm, doc: com.google.gson.JsonObject) {
        val id = JsonUtils.getString("_id", doc)
        var certification = realm.where(RealmCertification::class.java).equalTo("_id", id).findFirst()
        if (certification == null) {
            certification = realm.createObject(RealmCertification::class.java, id)
        }
        certification?.name = JsonUtils.getString("name", doc)
        certification?.setCourseIds(JsonUtils.getJsonArray("courseIds", doc))
    }
}
