package org.ole.planet.myplanet.data.repository

import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmUserModel

interface CourseRepository {
    fun getAllCourses(): List<RealmMyCourse>
    fun getCourseById(id: String): RealmMyCourse?
    fun getEnrolledCourses(): List<RealmMyCourse>
}

class CourseRepositoryImpl(
    private val databaseService: DatabaseService,
    private val apiInterface: ApiInterface
) : CourseRepository {
    override fun getAllCourses(): List<RealmMyCourse> {
        return databaseService.realmInstance.where(RealmMyCourse::class.java).findAll()
    }

    override fun getCourseById(id: String): RealmMyCourse? {
        return databaseService.realmInstance.where(RealmMyCourse::class.java)
            .equalTo("courseId", id)
            .findFirst()
    }

    override fun getEnrolledCourses(): List<RealmMyCourse> {
        return databaseService.realmInstance.where(RealmMyCourse::class.java)
            .equalTo("userId", getCurrentUserId())
            .findAll()
    }

    private fun getCurrentUserId(): String {
        return databaseService.realmInstance.where(RealmUserModel::class.java)
            .findFirst()?.id ?: ""
    }
}
