package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyCourse

interface CourseRepository {
    fun getAllCourses(): List<RealmMyCourse>
    fun getCourseById(id: String): RealmMyCourse?
    fun getEnrolledCourses(): List<RealmMyCourse>
}
