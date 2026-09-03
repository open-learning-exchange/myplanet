package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.ole.planet.myplanet.model.UserEntity

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id OR _id = :id LIMIT 1")
    suspend fun getById(id: String): UserEntity?
    @Query("SELECT * FROM users WHERE id IN (:userIds) OR _id IN (:userIds)")
    suspend fun getUsersByAnyIds(userIds: List<String>): List<UserEntity>
    @Query("SELECT * FROM users") suspend fun getAll(): List<UserEntity>
    @Query("SELECT * FROM users WHERE name = :name LIMIT 1") suspend fun getByName(name: String): UserEntity?
    @Query("SELECT * FROM users WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun getByNameIgnoreCase(name: String): UserEntity?
    @Query("SELECT * FROM users WHERE name LIKE '%' || :query || '%' OR firstName LIKE '%' || :query || '%' OR lastName LIKE '%' || :query || '%'") suspend fun search(query: String): List<UserEntity>
    @Query("SELECT COUNT(*) FROM users") suspend fun count(): Int
    @Query("DELETE FROM users WHERE id = :id") suspend fun deleteById(id: String): Int
    @Query("DELETE FROM users WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>): Int
    @Upsert suspend fun upsert(item: UserEntity)
    @Upsert suspend fun upsertAll(items: List<UserEntity>)

    @Query("SELECT * FROM users WHERE (_id IS NOT NULL AND TRIM(_id) != '') AND SUBSTR(id, 1, 5) != 'guest'")
    suspend fun getSyncedUsers(): List<UserEntity>

    @Query("SELECT * FROM users WHERE _id IS NOT NULL AND TRIM(_id) != ''")
    suspend fun getUsersForHealthSync(): List<UserEntity>

    @Query("SELECT * FROM users WHERE (_id IS NULL OR TRIM(_id) = '') OR isUpdated = 1 ORDER BY id LIMIT :limit")
    suspend fun getPendingSyncUsers(limit: Int): List<UserEntity>

    @Query("SELECT * FROM users WHERE name = :name AND SUBSTR(_id, 1, 6) = 'guest_' LIMIT 1")
    suspend fun getGuestUserByName(name: String): UserEntity?

    @Query("SELECT * FROM users WHERE name IN (:names) AND SUBSTR(_id, 1, 6) = 'guest_'")
    suspend fun getGuestUsersByNames(names: List<String>): List<UserEntity>

    @Query("""
        SELECT * FROM users
        WHERE IFNULL(name, '') IN (
            SELECT IFNULL(name, '') FROM users
            GROUP BY IFNULL(name, '')
            HAVING COUNT(*) > 1
        )
    """)
    suspend fun getDuplicateUsers(): List<UserEntity>
}
