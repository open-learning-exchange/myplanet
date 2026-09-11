package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import org.ole.planet.myplanet.model.CourseProgress

@Dao
interface CourseProgressDao {
    @Query("SELECT * FROM course_progress WHERE userId IS :userId AND courseId IN (:courseIds)")
    suspend fun getByUserAndCourseIds(userId: String?, courseIds: List<String>): List<CourseProgress>

    @Query("SELECT * FROM course_progress WHERE userId IS :userId AND courseId IS :courseId")
    suspend fun getByUserAndCourse(userId: String?, courseId: String?): List<CourseProgress>

    @Query("SELECT * FROM course_progress WHERE userId IS :userId")
    suspend fun getByUser(userId: String?): List<CourseProgress>

    @Query("SELECT * FROM course_progress WHERE courseId IS :courseId AND userId IS :userId AND stepNum = :stepNum LIMIT 1")
    suspend fun findByCourseUserAndStep(courseId: String?, userId: String?, stepNum: Int): CourseProgress?

    @Query("SELECT * FROM course_progress WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<CourseProgress>

    @RawQuery
    suspend fun getByExactKeysRaw(query: SupportSQLiteQuery): List<CourseProgress>

    suspend fun getByCourseUsersAndSteps(tuples: List<Triple<String, String, Int>>): List<CourseProgress> {
        if (tuples.isEmpty()) return emptyList()
        val distinctTuples = tuples.distinct()
        val results = ArrayList<CourseProgress>()
        distinctTuples.chunked(250).forEach { chunk ->
            val clauses = chunk.joinToString(" OR ") { "(courseId IS ? AND userId IS ? AND stepNum = ?)" }
            val sql = "SELECT * FROM course_progress WHERE $clauses"
            val bindArgs = ArrayList<Any?>(chunk.size * 3)
            for ((courseId, userId, stepNum) in chunk) {
                bindArgs.add(courseId)
                bindArgs.add(userId)
                bindArgs.add(stepNum)
            }
            results.addAll(getByExactKeysRaw(SimpleSQLiteQuery(sql, bindArgs.toTypedArray())))
        }
        return results
    }

    @Query("SELECT * FROM course_progress WHERE _id IS NULL AND userId NOT LIKE 'guest%'")
    suspend fun getPendingUploads(): List<CourseProgress>

    @Query("UPDATE course_progress SET _id = :remoteId, _rev = :rev WHERE id = :localId")
    suspend fun markUploaded(localId: String, remoteId: String, rev: String): Int

    @Query("UPDATE course_progress SET passed = :passed WHERE courseId = :courseId AND stepNum = :stepNum AND userId IS :userId")
    suspend fun updatePassedByCourseAndStep(courseId: String, stepNum: Int, passed: Boolean, userId: String?): Int

    @Upsert
    suspend fun upsert(progress: CourseProgress)

    @Upsert
    suspend fun upsertAll(progress: List<CourseProgress>)
}
