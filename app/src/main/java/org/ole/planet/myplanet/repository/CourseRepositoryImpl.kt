package org.ole.planet.myplanet.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.model.RealmStepExam

class CourseRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), CourseRepository {

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

    override suspend fun getCourseOnlineResources(courseId: String?): List<RealmMyLibrary> {
        return getCourseResources(courseId, isOffline = false)
    }

    override suspend fun getCourseOfflineResources(courseId: String?): List<RealmMyLibrary> {
        return getCourseResources(courseId, isOffline = true)
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

    override suspend fun getAllCourses(userId: String): List<RealmMyCourse> {
        return withRealmAsync { realm ->
            val myLibItems = RealmMyCourse.getMyCourseByUserId(
                userId,
                realm.where(RealmMyCourse::class.java).findAll()
            )
            val results = realm.where(RealmMyCourse::class.java)
                .isNotEmpty("courseTitle")
                .findAll()

            // Note: results is RealmResults, we need to pass a List to getOurCourse
            val ourCourseItems = RealmMyCourse.getOurCourse(userId, results)

            val combinedList = mutableListOf<RealmMyCourse>()
            myLibItems.forEach { course ->
                combinedList.add(course)
            }
            ourCourseItems.forEach { course ->
                if (!combinedList.any { it.id == course.id }) {
                    combinedList.add(course)
                }
            }

            val unmanagedList = realm.copyFromRealm(combinedList)

            // Now safely set isMyCourse on unmanaged objects
            unmanagedList.forEach { course ->
                 if (course.userId?.contains(userId) == true) {
                     course.isMyCourse = true
                 } else {
                     course.isMyCourse = false
                 }
            }

            unmanagedList
        }
    }

    override suspend fun getMyLibCourses(userId: String): List<RealmMyCourse> {
        return withRealmAsync { realm ->
            val results = realm.where(RealmMyCourse::class.java).findAll()
            val myCourses = RealmMyCourse.getMyCourseByUserId(userId, results)

            val unmanagedList = realm.copyFromRealm(myCourses)

            // Set isMyCourse flag for consistency
            unmanagedList.forEach { it.isMyCourse = true }

            unmanagedList
        }
    }

    override suspend fun getLibraryResourcesForCourses(courseIds: List<String>): List<RealmMyLibrary> {
        if (courseIds.isEmpty()) return emptyList()
        return withRealmAsync { realm ->
            val res = realm.where(RealmMyLibrary::class.java)
                .`in`("courseId", courseIds.toTypedArray())
                .equalTo("resourceOffline", false)
                .isNotNull("resourceLocalAddress")
                .findAll()
            realm.copyFromRealm(res)
        }
    }
}
