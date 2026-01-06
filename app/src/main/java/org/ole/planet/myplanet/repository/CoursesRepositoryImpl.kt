package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.utilities.JsonUtils

class CoursesRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), CoursesRepository {

    override suspend fun getMyCoursesFlow(userId: String): Flow<List<RealmMyCourse>> {
        return queryListFlow(RealmMyCourse::class.java) {
            equalTo("userId", userId)
        }
    }

    override suspend fun getCourseByCourseId(courseId: String?): RealmMyCourse? {
        if (courseId.isNullOrBlank()) {
            return null
        }
        return findByField(RealmMyCourse::class.java, "courseId", courseId)
    }

    override suspend fun getDetachedCourseById(courseId: String?): RealmMyCourse? {
        if (courseId.isNullOrBlank()) {
            return null
        }
        return withRealm { realm ->
            val course = realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", courseId)
                .findFirst()
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

    override suspend fun getCourseSteps(courseId: String?): List<RealmCourseStep> {
        if (courseId.isNullOrEmpty()) {
            return emptyList()
        }
        return queryList(RealmCourseStep::class.java) {
            equalTo("courseId", courseId)
        }
    }

    override suspend fun getCourseStep(stepId: String): RealmCourseStep? {
        return findByField(RealmCourseStep::class.java, "id", stepId)
    }

    override suspend fun getStepExams(stepId: String, type: String): List<RealmStepExam> {
        return queryList(RealmStepExam::class.java) {
            equalTo("stepId", stepId)
            equalTo("type", type)
        }
    }

    override suspend fun isMyCourse(userId: String?, courseId: String?): Boolean {
        if (userId.isNullOrBlank() || courseId.isNullOrBlank()) {
            return false
        }
        return count(RealmMyCourse::class.java) {
            equalTo("userId", userId)
            equalTo("courseId", courseId)
        } > 0
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
            val filter = JsonObject()

            filter.add("tags", RealmTag.getTagsArray(tags))
            filter.addProperty("doc.gradeLevel", grade)
            filter.addProperty("doc.subjectLevel", subject)
            activity.filter = JsonUtils.gson.toJson(filter)
        }
    }

    override suspend fun joinCourse(courseId: String, userId: String) {
        executeTransaction { realm ->
            val course = realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", courseId)
                .findFirst()
            course?.setUserId(userId)
            RealmRemovedLog.onAdd(realm, "courses", userId, courseId)
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
}
