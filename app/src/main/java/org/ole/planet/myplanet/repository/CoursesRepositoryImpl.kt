package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmResults
import java.util.ArrayList
import java.util.Calendar
import java.util.Collections
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.CourseProgressData
import org.ole.planet.myplanet.model.CourseStepData
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCertification
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.createStepResource
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmStepExam.Companion.insertCourseStepsExams
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.utils.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils

class CoursesRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val progressRepository: ProgressRepository,
    @AppPreferences private val preferences: SharedPreferences
) : RealmRepository(databaseService), CoursesRepository {

    private val concatenatedLinks = Collections.synchronizedList(ArrayList<String>())

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
            val course = realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", courseId)
                .findFirst()
            course?.let { realm.copyFromRealm(it) }
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
            if (steps != null) realm.copyFromRealm(steps) else emptyList()
        }
    }

    override suspend fun getCourseStepIds(courseId: String): Array<String?> {
        if (courseId.isBlank()) {
            return emptyArray()
        }
        return withRealm { realm ->
            val course = realm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
            course?.courseSteps?.map { it.id }?.toTypedArray() ?: emptyArray()
        }
    }

    override suspend fun markCourseAdded(courseId: String, userId: String?): Boolean {
        if (courseId.isBlank()) {
            return false
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

        return courseFound
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

    override suspend fun filterCourses(
        searchText: String,
        gradeLevel: String,
        subjectLevel: String,
        tagNames: List<String>
    ): List<RealmMyCourse> {
        return withRealm { realm ->
            val courseIdsWithTags = if (tagNames.isNotEmpty()) {
                val tagIds = realm.where(org.ole.planet.myplanet.model.RealmTag::class.java)
                    .`in`("name", tagNames.toTypedArray())
                    .findAll()
                    .map { it.id }

                realm.where(org.ole.planet.myplanet.model.RealmTag::class.java)
                    .equalTo("db", "courses")
                    .`in`("tagId", tagIds.toTypedArray())
                    .findAll()
                    .map { it.linkId }
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

    override suspend fun joinCourse(courseId: String, userId: String) {
        if (courseId.isBlank() || userId.isBlank()) return

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

    override suspend fun leaveCourse(courseId: String, userId: String) {
        executeTransaction { realm ->
            val course = realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", courseId)
                .findFirst()
            course?.removeUserId(userId)
            RealmRemovedLog.onRemove(realm, "courses", userId, courseId)
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

    override suspend fun getCourseProgress(courseId: String, userId: String?): org.ole.planet.myplanet.model.CourseProgressData? {
        val stepsList = getCourseSteps(courseId)
        val current = progressRepository.getCurrentProgress(stepsList, userId, courseId)
        return withRealm { realm ->
            val max = stepsList.size
            val course = realm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
            val title = course?.courseTitle

            val array = com.google.gson.JsonArray()
            stepsList.forEach { step ->
                val ob = com.google.gson.JsonObject()
                ob.addProperty("stepId", step.id)
                val exams = realm.where(RealmStepExam::class.java).equalTo("stepId", step.id).findAll()
                getExamObject(realm, exams, ob, userId)
                array.add(ob)
            }
            org.ole.planet.myplanet.model.CourseProgressData(title, current, max, array)
        }
    }

    private fun getExamObject(
        realm: io.realm.Realm,
        exams: io.realm.RealmResults<RealmStepExam>,
        ob: com.google.gson.JsonObject,
        userId: String?
    ) {
        exams.forEach { it ->
            it.id?.let { it1 ->
                realm.where(org.ole.planet.myplanet.model.RealmSubmission::class.java).equalTo("userId", userId)
                    .contains("parentId", it1).equalTo("type", "exam").findAll()
            }?.map {
                val answers = realm.where(org.ole.planet.myplanet.model.RealmAnswer::class.java).equalTo("submissionId", it.id).findAll()
                var examId = it.parentId
                if (it.parentId?.contains("@") == true) {
                    examId = it.parentId!!.split("@")[0]
                }
                val questions = realm.where(org.ole.planet.myplanet.model.RealmExamQuestion::class.java).equalTo("examId", examId).findAll()
                val questionCount = questions.size
                if (questionCount == 0) {
                    ob.addProperty("completed", false)
                    ob.addProperty("percentage", 0)
                } else {
                    ob.addProperty("completed", answers.size == questionCount)
                    val percentage = (answers.size.toDouble() / questionCount) * 100
                    ob.addProperty("percentage", percentage)
                }
                ob.addProperty("status", it.status)
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

    override suspend fun deleteCourseProgress(courseId: String) {
        executeTransaction { realm ->
            realm.where(RealmCourseProgress::class.java)
                .equalTo("courseId", courseId)
                .findAll()
                .deleteAllFromRealm()
            val examList: List<RealmStepExam> = realm.where(RealmStepExam::class.java)
                .equalTo("courseId", courseId)
                .findAll()
            for (exam in examList) {
                realm.where(RealmSubmission::class.java)
                    .equalTo("parentId", exam.id)
                    .notEqualTo("type", "survey")
                    .equalTo("uploaded", false)
                    .findAll()
                    .deleteAllFromRealm()
            }
        }
    }

    override suspend fun enrollUserInCourse(courseId: String, userId: String) {
        joinCourse(courseId, userId)
    }

    override suspend fun createCourses(courseDocs: List<JsonObject>, userId: String?) {
        if (courseDocs.isEmpty()) return
        val baseUrl = UrlUtils.getUrl()
        executeTransaction { realm ->
            courseDocs.forEach { myCoursesDoc ->
                val id = JsonUtils.getString("_id", myCoursesDoc)
                var myMyCoursesDB = realm.where(RealmMyCourse::class.java).equalTo("id", id).findFirst()
                if (myMyCoursesDB == null) {
                    myMyCoursesDB = realm.createObject(RealmMyCourse::class.java, id)
                }
                myMyCoursesDB?.setUserId(userId)
                myMyCoursesDB?.courseId = JsonUtils.getString("_id", myCoursesDoc)
                myMyCoursesDB?.courseRev = JsonUtils.getString("_rev", myCoursesDoc)
                myMyCoursesDB?.languageOfInstruction = JsonUtils.getString("languageOfInstruction", myCoursesDoc)
                myMyCoursesDB?.courseTitle = JsonUtils.getString("courseTitle", myCoursesDoc)
                myMyCoursesDB?.memberLimit = JsonUtils.getInt("memberLimit", myCoursesDoc)
                myMyCoursesDB?.description = JsonUtils.getString("description", myCoursesDoc)
                val description = JsonUtils.getString("description", myCoursesDoc)
                val links = extractLinks(description)
                for (link in links) {
                    val concatenatedLink = "$baseUrl/$link"
                    concatenatedLinks.add(concatenatedLink)
                }
                myMyCoursesDB?.method = JsonUtils.getString("method", myCoursesDoc)
                myMyCoursesDB?.gradeLevel = JsonUtils.getString("gradeLevel", myCoursesDoc)
                myMyCoursesDB?.subjectLevel = JsonUtils.getString("subjectLevel", myCoursesDoc)
                myMyCoursesDB?.createdDate = JsonUtils.getLong("createdDate", myCoursesDoc)
                myMyCoursesDB?.setNumberOfSteps(JsonUtils.getJsonArray("steps", myCoursesDoc).size())
                val courseStepsJsonArray = JsonUtils.getJsonArray("steps", myCoursesDoc)
                val courseStepsList = mutableListOf<RealmCourseStep>()

                for (i in 0 until courseStepsJsonArray.size()) {
                    val stepId = Base64.encodeToString(courseStepsJsonArray[i].toString().toByteArray(), Base64.NO_WRAP)
                    val stepJson = courseStepsJsonArray[i].asJsonObject
                    val step = RealmCourseStep()
                    step.id = stepId
                    step.stepTitle = JsonUtils.getString("stepTitle", stepJson)
                    step.description = JsonUtils.getString("description", stepJson)
                    val stepDescription = JsonUtils.getString("description", stepJson)
                    val stepLinks = extractLinks(stepDescription)
                    for (stepLink in stepLinks) {
                        val concatenatedLink = "$baseUrl/$stepLink"
                        concatenatedLinks.add(concatenatedLink)
                    }
                    val resources = JsonUtils.getJsonArray("resources", stepJson)
                    resources.forEach { resource ->
                        createStepResource(realm, resource.asJsonObject, myMyCoursesDB?.courseId, stepId)
                    }

                    if (stepJson.has("exam")) {
                        val `object` = stepJson.getAsJsonObject("exam")
                        `object`.addProperty("stepNumber", i + 1)
                        insertCourseStepsExams(myMyCoursesDB?.courseId, stepId, `object`, realm)
                    }

                    if (stepJson.has("survey")) {
                        val `object` = stepJson.getAsJsonObject("survey")
                        `object`.addProperty("stepNumber", i + 1)
                        `object`.addProperty("createdDate", myMyCoursesDB?.createdDate)
                        insertCourseStepsExams(myMyCoursesDB?.courseId, stepId, `object`, realm)
                    }

                    step.noOfResources = JsonUtils.getJsonArray("resources", stepJson).size()
                    step.courseId = myMyCoursesDB?.courseId
                    courseStepsList.add(step)
                }
                myMyCoursesDB?.courseSteps = RealmList()
                myMyCoursesDB?.courseSteps?.addAll(courseStepsList)
            }
        }
    }

    override suspend fun saveConcatenatedLinks() {
        val existingJsonLinks = preferences.getString("concatenated_links", null)
        val existingConcatenatedLinks = if (existingJsonLinks != null) {
            JsonUtils.gson.fromJson(existingJsonLinks, Array<String>::class.java).toMutableList()
        } else {
            mutableListOf()
        }
        val linksToProcess: List<String>
        synchronized(concatenatedLinks) {
            linksToProcess = concatenatedLinks.toList()
            concatenatedLinks.clear()
        }
        for (link in linksToProcess) {
            if (!existingConcatenatedLinks.contains(link)) {
                existingConcatenatedLinks.add(link)
            }
        }
        val jsonConcatenatedLinks = JsonUtils.gson.toJson(existingConcatenatedLinks)
        preferences.edit { putString("concatenated_links", jsonConcatenatedLinks) }
    }
}
