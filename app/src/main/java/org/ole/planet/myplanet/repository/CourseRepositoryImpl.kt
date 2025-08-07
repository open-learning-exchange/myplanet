package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmUserModel

class CourseRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : CourseRepository {

    override suspend fun getAllCourses(): List<RealmMyCourse> {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmMyCourse::class.java).findAll()
        }
    }

    override suspend fun getCourseById(id: String): RealmMyCourse? {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", id)
                .findFirst()
        }
    }

    override suspend fun getEnrolledCourses(): List<RealmMyCourse> {
        return databaseService.withRealmAsync { realm ->
            val userId = getCurrentUserId(realm)
            realm.where(RealmMyCourse::class.java)
                .equalTo("userId", userId)
                .findAll()
        }
    }

    override suspend fun getCoursesByUserId(userId: String): List<RealmMyCourse> {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmMyCourse::class.java)
                .equalTo("userId", userId)
                .findAll()
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

    private fun getCurrentUserId(realm: io.realm.Realm): String {
        return realm.where(RealmUserModel::class.java)
            .findFirst()?.id ?: ""
    }
}
