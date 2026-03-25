package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.DatabaseService
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
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.utils.JsonUtils

class CoursesRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val progressRepository: ProgressRepository,
    private val activitiesRepository: ActivitiesRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val tagsRepository: TagsRepository,
    private val ratingsRepository: RatingsRepository
) : RealmRepository(databaseService), CoursesRepository {

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

    override fun getMyCourses(userId: String?, courses: List<RealmMyCourse>): List<RealmMyCourse> {
        val myCourses: MutableList<RealmMyCourse> = ArrayList()
        if (userId == null) return myCourses
        for (course in courses) {
            if (course.userId?.contains(userId) == true) {
                myCourses.add(course)
            }
        }
        return myCourses
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
        return withContext(databaseService.ioDispatcher) {
            runCatching {
                if (courseId.isBlank()) {
                    return@runCatching false
                }

                var courseFound = false
                executeTransaction { realm ->
                    realm.where(RealmMyCourse::class.java)
                        .equalTo("courseId", courseId)
                        .findFirst()
                        ?.let { course ->
                            course.setUserId(userId)
                            if (!userId.isNullOrBlank()) {
                                realm.where(RealmRemovedLog::class.java)
                                    .equalTo("type", "courses")
                                    .equalTo("userId", userId)
                                    .equalTo("docId", course.courseId)
                                    .findAll()
                                    .deleteAllFromRealm()
                            }
                            courseFound = true
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

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")

    private fun normalizeText(str: String): String {
        return Normalizer.normalize(str.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
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

                if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                    startsWithQuery.add(item)
                } else if (normalizedQueryParts.all { title.contains(it, ignoreCase = true) }) {
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
                realm.where(RealmTag::class.java)
                    .equalTo("db", "courses")
                    .`in`("name", tagNames.toTypedArray())
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
                val allAnswers = mutableListOf<RealmAnswer>()
                // Realm IN query limit is around 1000 items, so we chunk the list to avoid query length limits.
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
            for (course in myCourses) {
                ids.add(course.courseId)
            }
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
            val data = realm.where(RealmMyCourse::class.java).findAll()

            var list: List<RealmMyCourse> = if (query.isEmpty()) {
                realm.copyFromRealm(data)
            } else {
                val queryParts = query.split(" ").filterNot { it.isEmpty() }
                val normalizedQueryParts = queryParts.map { normalizeText(it) }
                val normalizedQuery = normalizeText(query)
                val startsWithQuery = mutableListOf<RealmMyCourse>()
                val containsQuery = mutableListOf<RealmMyCourse>()

                for (item in data) {
                    val title = item.courseTitle?.let { normalizeText(it) } ?: continue

                    if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                        startsWithQuery.add(item)
                    } else if (normalizedQueryParts.all { title.contains(it, ignoreCase = true) }) {
                        containsQuery.add(item)
                    }
                }
                val filteredData = startsWithQuery + containsQuery
                realm.copyFromRealm(filteredData)
            }

            list = if (isMyCourseLib) {
                getMyCourses(userId, list)
            } else {
                RealmMyCourse.getAllCourses(userId, list)
            }

            if (tags.isEmpty()) {
                return@withRealm list
            }

            val tagIds = tags.mapNotNull { it.id }.toTypedArray()
            val linkedCourseIds = realm.where(RealmTag::class.java)
                .equalTo("db", "courses")
                .`in`("tagId", tagIds)
                .findAll()
                .mapNotNull { it.linkId }
                .toSet()

            list.filter { linkedCourseIds.contains(it.courseId) }.distinct()
        }
    }
}
