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
    @Query("SELECT * FROM users WHERE name = :name LIMIT 1") suspend fun getByName(name: String): UserEntity?
    @Query("SELECT * FROM users WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun getByNameIgnoreCase(name: String): UserEntity?
    @Query("SELECT * FROM users WHERE name LIKE '%' || :query || '%' OR firstName LIKE '%' || :query || '%' OR lastName LIKE '%' || :query || '%'") suspend fun search(query: String): List<UserEntity>
    @Query("SELECT * FROM users WHERE name IN (SELECT name FROM users GROUP BY name HAVING COUNT(*) > 1)") suspend fun getDuplicateUsers(): List<UserEntity>
    @Query("SELECT COUNT(*) FROM users") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM users WHERE planetCode = :planetCode") suspend fun countByPlanetCode(planetCode: String): Int
    @Query("DELETE FROM users WHERE id = :id") suspend fun deleteById(id: String): Int
    @Query("DELETE FROM users WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>): Int
    @Upsert suspend fun upsert(item: UserEntity)
    @Upsert suspend fun upsertAll(items: List<UserEntity>)
}