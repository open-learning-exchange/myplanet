package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.ole.planet.myplanet.model.CourseStep

data class CourseStepCount(val courseId: String?, val count: Int)

@Dao
interface CourseStepDao {
    @Query("SELECT * FROM course_steps WHERE courseId = :courseId") suspend fun getByCourseId(courseId: String): List<CourseStep>
    @Query("SELECT * FROM course_steps WHERE courseId IN (:courseIds)") suspend fun getByCourseIdsInternal(courseIds: List<String>): List<CourseStep>
    suspend fun getByCourseIds(courseIds: List<String>): List<CourseStep> {
        if (courseIds.isEmpty()) return emptyList()
        return courseIds.distinct().chunked(900).flatMap { getByCourseIdsInternal(it) }
    }
    @Query("SELECT courseId, COUNT(*) AS count FROM course_steps WHERE courseId IN (:courseIds) GROUP BY courseId")
    suspend fun countByCourseIdsInternal(courseIds: List<String>): List<CourseStepCount>
    suspend fun countByCourseIds(courseIds: List<String>): Map<String, Int> {
        if (courseIds.isEmpty()) return emptyMap()
        return courseIds.distinct().chunked(900)
            .flatMap { countByCourseIdsInternal(it) }
            .mapNotNull { count -> count.courseId?.let { it to count.count } }
            .toMap()
    }
    @Query("SELECT * FROM course_steps WHERE id = :id LIMIT 1") suspend fun getById(id: String): CourseStep?
    @Upsert suspend fun upsertAll(items: List<CourseStep>)
    @Query("DELETE FROM course_steps WHERE courseId = :courseId AND id NOT IN (:keepIds)") suspend fun deleteStaleInternal(courseId: String, keepIds: List<String>)
    @Query("DELETE FROM course_steps WHERE courseId = :courseId") suspend fun deleteByCourseId(courseId: String)

    suspend fun deleteStaleSteps(courseId: String, keepIds: List<String>) {
        if (keepIds.isEmpty()) deleteByCourseId(courseId) else deleteStaleInternal(courseId, keepIds.distinct())
    }
}
