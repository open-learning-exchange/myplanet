package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.MyLibrary

/**
 * DAO for resources ([MyLibrary]).
 *
 * Shelf membership was a `RealmList<String>` queried with Realm's list-`equalTo` (contains). Here
 * `userId` is a JSON string column, so membership is matched with `LIKE :userPattern ESCAPE '\'`;
 * the repository builds `userPattern` as `%"<escaped-userId>"%` so the quotes delimit exact list
 * entries. Non-membership is `(userId IS NULL OR userId NOT LIKE :userPattern ESCAPE '\')`.
 */
@Dao
interface MyLibraryDao {
    @RawQuery
    suspend fun filterByTitleNormal(query: SupportSQLiteQuery): List<MyLibrary>

    @Query("SELECT * FROM my_library")
    suspend fun getAll(): List<MyLibrary>

    @Query("SELECT * FROM my_library WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MyLibrary?

    @Query("SELECT * FROM my_library WHERE resourceId = :resourceId LIMIT 1")
    suspend fun getByResourceId(resourceId: String): MyLibrary?

    @Query("SELECT * FROM my_library WHERE _id = :underscoreId LIMIT 1")
    suspend fun getByUnderscoreId(underscoreId: String): MyLibrary?

    @Query("SELECT * FROM my_library WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<MyLibrary>

    @Query("SELECT * FROM my_library WHERE _id IN (:ids)")
    suspend fun getByUnderscoreIds(ids: List<String>): List<MyLibrary>

    @Query("SELECT * FROM my_library WHERE resourceId IN (:resourceIds)")
    suspend fun getByResourceIds(resourceIds: List<String>): List<MyLibrary>

    @Query("SELECT * FROM my_library WHERE isPrivate = 0")
    suspend fun getPublic(): List<MyLibrary>

    @Query("SELECT * FROM my_library WHERE isPrivate = 1 AND privateFor = :teamId")
    suspend fun getTeamPrivate(teamId: String): List<MyLibrary>

    @Query("SELECT * FROM my_library WHERE resourceLocalAddress = :localAddress")
    suspend fun getByLocalAddress(localAddress: String): List<MyLibrary>

    @Query("SELECT * FROM my_library WHERE stepId = :stepId")
    suspend fun getByStepId(stepId: String): List<MyLibrary>

    @Query("SELECT * FROM my_library WHERE courseId = :courseId")
    suspend fun getByCourseId(courseId: String): List<MyLibrary>

    @Query("SELECT * FROM my_library WHERE courseId IN (:courseIds)")
    suspend fun getByCourseIds(courseIds: List<String>): List<MyLibrary>

    @Query(
        "SELECT * FROM my_library WHERE courseId IN (:courseIds) " +
            "AND resourceOffline = 0 AND resourceLocalAddress IS NOT NULL"
    )
    suspend fun getOfflineResourcesForCourses(courseIds: List<String>): List<MyLibrary>

    @Query(
        "SELECT * FROM my_library WHERE courseId = :courseId " +
            "AND resourceOffline = :isOffline AND resourceLocalAddress IS NOT NULL"
    )
    suspend fun getCourseResources(courseId: String, isOffline: Boolean): List<MyLibrary>

    @Query("SELECT * FROM my_library WHERE resourceId IS NOT NULL")
    suspend fun getWithResourceId(): List<MyLibrary>

    @Query("SELECT COUNT(*) FROM my_library WHERE title = :title COLLATE NOCASE")
    suspend fun countByTitle(title: String): Int

    @Query(
        "SELECT * FROM my_library " +
            "WHERE (resourceOffline = 0 OR (resourceLocalAddress IS NOT NULL AND _rev IS NOT downloadedRev))"
    )
    suspend fun getSyncable(): List<MyLibrary>

    @Query(
        "SELECT * FROM my_library WHERE isPrivate = 0 " +
            "AND (resourceOffline = 0 OR (resourceLocalAddress IS NOT NULL AND _rev IS NOT downloadedRev))"
    )
    suspend fun getPublicNeedingUpdate(): List<MyLibrary>

    @Query("SELECT * FROM my_library WHERE _rev IS NULL")
    suspend fun getPendingUploads(): List<MyLibrary>

    @Query(
        "SELECT * FROM my_library WHERE isPrivate = 1 AND mediaType = 'image' " +
            "AND createdDate > :timestamp"
    )
    suspend fun getPrivateImagesCreatedAfter(timestamp: Long): List<MyLibrary>

    @Query("SELECT * FROM my_library WHERE userId LIKE :userPattern ESCAPE '\\'")
    suspend fun getForUserPattern(userPattern: String): List<MyLibrary>

    @Query("SELECT * FROM my_library WHERE userId LIKE :userPattern ESCAPE '\\'")
    fun getForUserPatternFlow(userPattern: String): Flow<List<MyLibrary>>

    @Query("SELECT * FROM my_library WHERE isPrivate = 0 AND userId LIKE :userPattern ESCAPE '\\'")
    suspend fun getPublicForUserPattern(userPattern: String): List<MyLibrary>

    @Query(
        "SELECT * FROM my_library WHERE isPrivate = 0 " +
            "AND userId LIKE :userPattern ESCAPE '\\' " +
            "AND (resourceOffline = 0 OR (resourceLocalAddress IS NOT NULL AND _rev IS NOT downloadedRev))"
    )
    suspend fun getPublicNeedingUpdateForUserPattern(userPattern: String): List<MyLibrary>

    @Query(
        "SELECT COUNT(*) FROM my_library WHERE isPrivate = 0 " +
            "AND userId LIKE :userPattern ESCAPE '\\' " +
            "AND (resourceOffline = 0 OR (resourceLocalAddress IS NOT NULL AND _rev IS NOT downloadedRev))"
    )
    suspend fun countPublicNeedingUpdateForUserPattern(userPattern: String): Int

    @Query(
        "SELECT * FROM my_library WHERE isPrivate = 0 " +
            "AND (userId IS NULL OR userId NOT LIKE :userPattern ESCAPE '\\')"
    )
    suspend fun getPublicNotUserPattern(userPattern: String): List<MyLibrary>

    @Query(
        "SELECT * FROM my_library WHERE userId LIKE :userPattern ESCAPE '\\' " +
            "ORDER BY createdDate DESC LIMIT 10"
    )
    fun getRecentForUserPatternFlow(userPattern: String): Flow<List<MyLibrary>>

    @Query(
        "SELECT id FROM my_library WHERE userId LIKE :userPattern ESCAPE '\\' " +
            "AND resourceOffline = 0 AND resourceLocalAddress IS NOT NULL"
    )
    fun getPendingDownloadsForUserPatternFlow(userPattern: String): Flow<List<String>>

    @Query(
        "SELECT * FROM my_library WHERE resourceId IN (:resourceIds) " +
            "AND (userId IS NULL OR userId NOT LIKE :userPattern ESCAPE '\\')"
    )
    suspend fun getByResourceIdsNotUserPattern(resourceIds: List<String>, userPattern: String): List<MyLibrary>

    @Query("SELECT * FROM my_library WHERE resourceId IN (:resourceIds) AND resourceOffline = 1")
    suspend fun getOfflineByResourceIds(resourceIds: List<String>): List<MyLibrary>

    @Query("UPDATE my_library SET resourceOffline = 0 WHERE resourceId IN (:ids) AND resourceOffline = 1")
    suspend fun markAsNotOfflineByResourceIds(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MyLibrary)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MyLibrary>)

    @Query("DELETE FROM my_library WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT id FROM my_library WHERE userId LIKE :userPattern ESCAPE '\\'")
    suspend fun getIdsForUserPattern(userPattern: String): List<String>

    @Query("SELECT resourceId, title FROM my_library WHERE resourceId IS NOT NULL")
    suspend fun getResourceTitles(): List<ResourceTitleProjection>

    @Query(
        "DELETE FROM my_library WHERE _rev IS NOT NULL AND _rev != '' AND isPrivate = 0 " +
            "AND resourceId NOT IN (:currentResourceIds)"
    )
    suspend fun deleteStalePublicNotIn(currentResourceIds: List<String>)

    @Query("DELETE FROM my_library WHERE _rev IS NOT NULL AND _rev != '' AND isPrivate = 0")
    suspend fun deleteAllStalePublic()
}

data class ResourceTitleProjection(
    val resourceId: String?,
    val title: String?
)
