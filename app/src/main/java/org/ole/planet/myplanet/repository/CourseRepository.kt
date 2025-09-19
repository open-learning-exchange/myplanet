package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary

interface CourseRepository {
    suspend fun getAllCourses(): List<RealmMyCourse>
    suspend fun updateMyCourseFlag(courseId: String, isMyCourse: Boolean)
    suspend fun updateMyCourseFlag(courseIds: List<String>, isMyCourse: Boolean) {
        courseIds.forEach { updateMyCourseFlag(it, isMyCourse) }
    }
    suspend fun getCourseByCourseId(courseId: String?): RealmMyCourse?
    suspend fun getCourseOnlineResources(courseId: String?): List<RealmMyLibrary>
    suspend fun getCourseOfflineResources(courseId: String?): List<RealmMyLibrary>
    suspend fun getCourseExamCount(courseId: String?): Int
    suspend fun getCourseSteps(courseId: String?): List<RealmCourseStep>
}
