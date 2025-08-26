package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.datamanager.DatabaseService

interface CourseProgressRepository {
    val databaseService: DatabaseService
    fun getCourseProgress(userId: String?): Map<String, JsonObject>
    fun fetchCourseData(realm: Realm, userId: String?): JsonArray
    fun getCourseProgress(courseData: JsonArray, courseId: String): JsonObject?
    fun countUsersWhoCompletedCourse(realm: Realm, courseId: String): Int
    fun getCourseSteps(userId: String?, courseId: String): JsonArray
}
