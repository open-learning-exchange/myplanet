package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.ole.planet.myplanet.model.CourseProgress

@Dao
interface CourseProgressDao {
    @Query("SELECT * FROM course_progress WHERE userId = :userId AND courseId IN (:courseIds)")
    suspend fun getByUserAndCourseIds(userId: String?, courseIds: List<String>): List<CourseProgress>

    @Query("SELECT * FROM course_progress WHERE userId = :userId AND courseId = :courseId")
    suspend fun getByUserAndCourse(userId: String?, courseId: String?): List<CourseProgress>

    @Query("SELECT * FROM course_progress WHERE userId = :userId")
    suspend fun getByUser(userId: String?): List<CourseProgress>

    @Query("SELECT * FROM course_progress WHERE courseId = :courseId AND userId = :userId AND stepNum = :stepNum LIMIT 1")
    suspend fun findByCourseUserAndStep(courseId: String?, userId: String?, stepNum: Int): CourseProgress?

    @Query("SELECT * FROM course_progress WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<CourseProgress>

    @Query("SELECT * FROM course_progress WHERE courseId IN (:courseIds) AND userId IN (:userIds) AND stepNum IN (:stepNums)")
    suspend fun getByCourseUsersAndSteps(courseIds: List<String>, userIds: List<String>, stepNums: List<Int>): List<CourseProgress>

    @Query("SELECT * FROM course_progress WHERE _id IS NULL AND userId NOT LIKE 'guest%'")
    suspend fun getPendingUploads(): List<CourseProgress>

    @Query("UPDATE course_progress SET _id = :remoteId, _rev = :rev WHERE id = :localId")
    suspend fun markUploaded(localId: String, remoteId: String, rev: String): Int

    @Query("UPDATE course_progress SET passed = :passed WHERE courseId = :courseId AND stepNum = :stepNum")
    suspend fun updatePassedByCourseAndStep(courseId: String, stepNum: Int, passed: Boolean): Int

    @Upsert
    suspend fun upsert(progress: CourseProgress)

    @Upsert
    suspend fun upsertAll(progress: List<CourseProgress>)
}
