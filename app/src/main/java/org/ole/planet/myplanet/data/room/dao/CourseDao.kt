package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.MyCourse

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses") suspend fun getAll(): List<MyCourse>
    @Query("SELECT * FROM courses WHERE courseId = :courseId OR id = :courseId LIMIT 1") suspend fun getByCourseId(courseId: String): MyCourse?
    @Query("SELECT * FROM courses WHERE courseId IN (:courseIds) OR id IN (:courseIds) OR _id IN (:courseIds)") suspend fun getByCourseIds(courseIds: List<String>): List<MyCourse>
    @Query("SELECT * FROM courses") fun observeAll(): Flow<List<MyCourse>>
    @Query("SELECT * FROM courses WHERE courseId = :courseId OR id = :courseId LIMIT 1") fun observeByCourseId(courseId: String): Flow<MyCourse?>
    @Query("DELETE FROM courses WHERE courseId = :courseId") suspend fun deleteByCourseId(courseId: String): Int
    @Query("DELETE FROM courses WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>): Int
    @Upsert suspend fun upsertAll(items: List<MyCourse>)
    @Upsert fun upsertAllBlocking(items: List<MyCourse>)
    @Upsert suspend fun upsert(item: MyCourse)
}