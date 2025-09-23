package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam.Companion.getNoOfExam

class CourseRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), CourseRepository {

    override suspend fun getAllCourses(): List<RealmMyCourse> {
        return queryList(RealmMyCourse::class.java)
    }

    override suspend fun updateMyCourseFlag(courseId: String, isMyCourse: Boolean) {
        update(RealmMyCourse::class.java, "courseId", courseId) { it.isMyCourse = isMyCourse }
    }

    override suspend fun updateMyCourseFlag(courseIds: List<String>, isMyCourse: Boolean) {
        executeTransaction { realm ->
            realm.where(RealmMyCourse::class.java)
                .`in`("courseId", courseIds.toTypedArray())
                .findAll()
                .forEach { it.isMyCourse = isMyCourse }
        }
    }

    override suspend fun getCoursesByIds(courseIds: Collection<String>): List<RealmMyCourse> {
        if (courseIds.isEmpty()) {
            return emptyList()
        }
        return queryList(RealmMyCourse::class.java) {
            `in`("id", courseIds.toTypedArray())
        }
    }

    override suspend fun getCourseByCourseId(courseId: String?): RealmMyCourse? {
        return courseId?.let { findByField(RealmMyCourse::class.java, "courseId", it) }
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
        return withRealm { realm ->
            getNoOfExam(realm, courseId)
        }
    }

    override suspend fun getCourseSteps(courseId: String?): List<RealmCourseStep> {
        if (courseId.isNullOrEmpty()) {
            return emptyList()
        }
        return withRealm { realm ->
            val steps = RealmMyCourse.getCourseSteps(realm, courseId)
            if (steps.isEmpty()) {
                emptyList()
            } else {
                realm.copyFromRealm(steps)
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
}
