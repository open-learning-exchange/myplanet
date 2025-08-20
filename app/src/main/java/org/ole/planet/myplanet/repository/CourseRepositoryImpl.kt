package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.findCopyByField
import org.ole.planet.myplanet.datamanager.queryList
import org.ole.planet.myplanet.model.RealmMyCourse

class CourseRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
) : CourseRepository {

    override suspend fun getAllCourses(): List<RealmMyCourse> {
        return databaseService.withRealmAsync { realm ->
            realm.queryList(RealmMyCourse::class.java)
        }
    }

    override suspend fun getCourseById(id: String): RealmMyCourse? {
        return databaseService.withRealmAsync { realm ->
            realm.findCopyByField(RealmMyCourse::class.java, "courseId", id)
        }
    }

    override suspend fun getEnrolledCourses(userId: String): List<RealmMyCourse> =
        getCoursesByUserId(userId)

    override suspend fun getCoursesByUserId(userId: String): List<RealmMyCourse> {
        return databaseService.withRealmAsync { realm ->
            realm.queryList(RealmMyCourse::class.java) {
                equalTo("userId", userId)
            }
        }
    }

    override suspend fun saveCourse(course: RealmMyCourse) {
        databaseService.executeTransactionAsync { realm ->
            realm.copyToRealmOrUpdate(course)
        }
    }

    override suspend fun updateCourse(id: String, updater: (RealmMyCourse) -> Unit) {
        databaseService.executeTransactionAsync { realm ->
            realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", id)
                .findFirst()
                ?.let { updater(it) }
        }
    }

    override suspend fun deleteCourse(id: String) {
        databaseService.executeTransactionAsync { realm ->
            realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", id)
                .findFirst()
                ?.deleteFromRealm()
        }
    }
}
