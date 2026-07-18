package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyLibrary

/**
 * DAO for resources ([RealmMyLibrary]).
 *
 * Shelf membership was a `RealmList<String>` queried with Realm's list-`equalTo` (contains). Here
 * `userId` is a JSON string column, so membership is matched with `LIKE :userPattern ESCAPE '\'`;
 * the repository builds `userPattern` as `%"<escaped-userId>"%` so the quotes delimit exact list
 * entries. Non-membership is `(userId IS NULL OR userId NOT LIKE :userPattern ESCAPE '\')`.
 */
@Dao
interface MyLibraryDao {
    @Query("SELECT * FROM my_library")
    suspend fun getAll(): List<RealmMyLibrary>

    @Query("SELECT * FROM my_library WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RealmMyLibrary?

    @Query("SELECT * FROM my_library WHERE resourceId = :resourceId LIMIT 1")
    suspend fun getByResourceId(resourceId: String): RealmMyLibrary?

    @Query("SELECT * FROM my_library WHERE _id = :underscoreId LIMIT 1")
    suspend fun getByUnderscoreId(underscoreId: String): RealmMyLibrary?

    @Query("SELECT * FROM my_library WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<RealmMyLibrary>

    @Query("SELECT * FROM my_library WHERE _id IN (:ids)")
    suspend fun getByUnderscoreIds(ids: List<String>): List<RealmMyLibrary>

    @Query("SELECT * FROM my_library WHERE resourceId IN (:resourceIds)")
    suspend fun getByResourceIds(resourceIds: List<String>): List<RealmMyLibrary>

    @Query("SELECT * FROM my_library WHERE isPrivate = 0")
    suspend fun getPublic(): List<RealmMyLibrary>

    @Query("SELECT * FROM my_library WHERE isPrivate = 1 AND privateFor = :teamId")
    suspend fun getTeamPrivate(teamId: String): List<RealmMyLibrary>

    @Query("SELECT * FROM my_library WHERE resourceLocalAddress = :localAddress")
    suspend fun getByLocalAddress(localAddress: String): List<RealmMyLibrary>

    @Query("SELECT * FROM my_library WHERE stepId = :stepId")
    suspend fun getByStepId(stepId: String): List<RealmMyLibrary>

    @Query("SELECT * FROM my_library WHERE courseId = :courseId")
    suspend fun getByCourseId(courseId: String): List<RealmMyLibrary>

    @Query("SELECT * FROM my_library WHERE courseId IN (:courseIds)")
    suspend fun getByCourseIds(courseIds: List<String>): List<RealmMyLibrary>

    @Query(
        "SELECT * FROM my_library WHERE courseId IN (:courseIds) " +
            "AND resourceOffline = 0 AND resourceLocalAddress IS NOT NULL"
    )
    suspend fun getOfflineResourcesForCourses(courseIds: List<String>): List<RealmMyLibrary>

    @Query(
        "SELECT * FROM my_library WHERE courseId = :courseId " +
            "AND resourceOffline = :isOffline AND resourceLocalAddress IS NOT NULL"
    )
    suspend fun getCourseResources(courseId: String, isOffline: Boolean): List<RealmMyLibrary>

    @Query("SELECT * FROM my_library WHERE resourceId IS NOT NULL")
    suspend fun getWithResourceId(): List<RealmMyLibrary>

    @Query("SELECT COUNT(*) FROM my_library WHERE title = :title COLLATE NOCASE")
    suspend fun countByTitle(title: String): Int

    @Query("SELECT * FROM my_library WHERE resourceOffline = 0")
    suspend fun getSyncable(): List<RealmMyLibrary>

    @Query("SELECT * FROM my_library WHERE _rev IS NULL")
    suspend fun getPendingUploads(): List<RealmMyLibrary>

    @Query(
        "SELECT * FROM my_library WHERE isPrivate = 1 AND mediaType = 'image' " +
            "AND createdDate > :timestamp"
    )
    suspend fun getPrivateImagesCreatedAfter(timestamp: Long): List<RealmMyLibrary>

    // --- shelf-membership (userId JSON list) ---

    @Query("SELECT * FROM my_library WHERE userId LIKE :userPattern ESCAPE '\\'")
    suspend fun getForUserPattern(userPattern: String): List<RealmMyLibrary>

    @Query("SELECT * FROM my_library WHERE isPrivate = 0 AND userId LIKE :userPattern ESCAPE '\\'")
    suspend fun getPublicForUserPattern(userPattern: String): List<RealmMyLibrary>

    @Query(
        "SELECT * FROM my_library WHERE isPrivate = 0 " +
            "AND (userId IS NULL OR userId NOT LIKE :userPattern ESCAPE '\\')"
    )
    suspend fun getPublicNotUserPattern(userPattern: String): List<RealmMyLibrary>

    @Query(
        "SELECT * FROM my_library WHERE userId LIKE :userPattern ESCAPE '\\' " +
            "ORDER BY createdDate DESC LIMIT 10"
    )
    fun getRecentForUserPatternFlow(userPattern: String): Flow<List<RealmMyLibrary>>

    @Query(
        "SELECT * FROM my_library WHERE userId LIKE :userPattern ESCAPE '\\' " +
            "AND resourceOffline = 0 AND resourceLocalAddress IS NOT NULL"
    )
    fun getPendingDownloadsForUserPatternFlow(userPattern: String): Flow<List<RealmMyLibrary>>

    @Query(
        "SELECT * FROM my_library WHERE resourceId IN (:resourceIds) " +
            "AND (userId IS NULL OR userId NOT LIKE :userPattern ESCAPE '\\')"
    )
    suspend fun getByResourceIdsNotUserPattern(resourceIds: List<String>, userPattern: String): List<RealmMyLibrary>

    @Query("SELECT * FROM my_library WHERE resourceId IN (:resourceIds) AND resourceOffline = 1")
    suspend fun getOfflineByResourceIds(resourceIds: List<String>): List<RealmMyLibrary>

    // --- writes ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RealmMyLibrary)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RealmMyLibrary>)

    @Query("DELETE FROM my_library WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE my_library SET resourceOffline = :isOffline")
    suspend fun setAllOffline(isOffline: Boolean)

    // removeDeletedResources: server-known public resources whose id fell out of the current set.
    @Query(
        "DELETE FROM my_library WHERE _rev IS NOT NULL AND _rev != '' AND isPrivate = 0 " +
            "AND resourceId NOT IN (:currentResourceIds)"
    )
    suspend fun deleteStalePublicNotIn(currentResourceIds: List<String>)

    @Query("DELETE FROM my_library WHERE _rev IS NOT NULL AND _rev != '' AND isPrivate = 0")
    suspend fun deleteAllStalePublic()
}
