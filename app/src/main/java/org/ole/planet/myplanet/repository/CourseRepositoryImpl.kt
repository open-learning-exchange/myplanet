package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam.Companion.getNoOfExam
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmStepExam

class CourseRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), CourseRepository {

    override suspend fun getAllCourses(): List<RealmMyCourse> {
        return queryList(RealmMyCourse::class.java)
    }

    override suspend fun addUserToCourses(courseIds: List<String>, userId: String) {
        if (courseIds.isEmpty()) return
        executeTransaction { realm ->
            val realmCourses = realm.where(RealmMyCourse::class.java)
                .`in`("courseId", courseIds.toTypedArray())
                .findAll()
            realmCourses.forEach { course ->
                course.setUserId(userId)
                course.isMyCourse = true
                RealmRemovedLog.clearRemovalLogs(realm, "courses", userId, course.courseId)
            }
        }
    }

    override suspend fun removeUserFromCourses(courseIds: List<String>, userId: String) {
        if (courseIds.isEmpty()) return
        executeTransaction { realm ->
            val courses = realm.where(RealmMyCourse::class.java)
                .`in`("courseId", courseIds.toTypedArray())
                .findAll()
            courses.forEach { course ->
                course.removeUserId(userId)
                course.isMyCourse = false
                RealmRemovedLog.createRemovalLog(realm, "courses", userId, course.courseId)
            }
            realm.where(RealmCourseProgress::class.java)
                .equalTo("userId", userId)
                .`in`("courseId", courseIds.toTypedArray())
                .findAll()
                .deleteAllFromRealm()
            val exams = realm.where(RealmStepExam::class.java)
                .`in`("courseId", courseIds.toTypedArray())
                .findAll()
            exams.forEach { exam ->
                realm.where(RealmSubmission::class.java)
                    .equalTo("parentId", exam.id)
                    .notEqualTo("type", "survey")
                    .equalTo("uploaded", false)
                    .findAll()
                    .deleteAllFromRealm()
            }
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
