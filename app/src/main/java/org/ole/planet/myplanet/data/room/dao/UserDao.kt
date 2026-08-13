package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.ole.planet.myplanet.model.UserEntity

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id OR _id = :id LIMIT 1")
    suspend fun getById(id: String): UserEntity?

    @Query("SELECT * FROM users") suspend fun getAll(): List<UserEntity>
    @Query("SELECT * FROM users WHERE id IN (:ids) OR _id IN (:ids)") suspend fun getByIds(ids: List<String>): List<UserEntity>
    @Query("SELECT * FROM users WHERE _id IS NOT NULL AND _id != '' AND id NOT LIKE 'guest%'") suspend fun getSyncedUsers(): List<UserEntity>
@Query("SELECT * FROM users WHERE _id IS NOT NULL AND _id != ''") suspend fun getUsersForHealthSync(): List<UserEntity>
    @Query("SELECT * FROM users WHERE _id IS NULL OR _id = '' OR isUpdated = 1 LIMIT :limit") suspend fun getPendingSyncUsers(limit: Int): List<UserEntity>
    @Query("SELECT * FROM users WHERE name = :name LIMIT 1") suspend fun getByName(name: String): UserEntity?
    @Query("SELECT * FROM users WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun getByNameIgnoreCase(name: String): UserEntity?
    @Query("SELECT * FROM users WHERE name LIKE '%' || :query || '%' OR firstName LIKE '%' || :query || '%' OR lastName LIKE '%' || :query || '%'") suspend fun search(query: String): List<UserEntity>

    @Query("SELECT * FROM users ORDER BY joinDate ASC") suspend fun getPatientsSortedByJoinDateAsc(): List<UserEntity>
    @Query("SELECT * FROM users ORDER BY joinDate DESC") suspend fun getPatientsSortedByJoinDateDesc(): List<UserEntity>
    @Query("SELECT * FROM users ORDER BY name ASC") suspend fun getPatientsSortedByNameAsc(): List<UserEntity>
    @Query("SELECT * FROM users ORDER BY name DESC") suspend fun getPatientsSortedByNameDesc(): List<UserEntity>

@Query("SELECT * FROM users WHERE IFNULL(name, '') IN (SELECT IFNULL(name, '') FROM users GROUP BY IFNULL(name, '') HAVING COUNT(*) > 1)") suspend fun getDuplicateUsers(): List<UserEntity>
@Query("SELECT * FROM users WHERE name IN (:names) AND _id LIKE 'guest_%'") suspend fun getGuestUsersByNames(names: List<String>): List<UserEntity>
    @Query("SELECT COUNT(*) FROM users") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM users WHERE planetCode = :planetCode") suspend fun countByPlanetCode(planetCode: String): Int
    @Query("DELETE FROM users WHERE id = :id") suspend fun deleteById(id: String): Int
    @Query("DELETE FROM users WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>): Int
    @Upsert suspend fun upsert(item: UserEntity)
    @Upsert suspend fun upsertAll(items: List<UserEntity>)
}