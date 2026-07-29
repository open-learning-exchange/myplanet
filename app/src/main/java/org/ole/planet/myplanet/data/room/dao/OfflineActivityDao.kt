package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.OfflineActivity

@Dao
interface OfflineActivityDao {
    @Query("SELECT COUNT(*) FROM offline_activity WHERE userId = :userId AND type = :type")
    suspend fun countByUserIdAndType(userId: String, type: String): Int

    @Query("SELECT COUNT(*) FROM offline_activity WHERE userName = :userName AND type = :type")
    suspend fun countByUserNameAndType(userName: String, type: String): Int

    @Query("SELECT * FROM offline_activity WHERE userName = :userName AND type = :type")
    fun observeByUserNameAndType(userName: String, type: String): Flow<List<OfflineActivity>>

    @Query("SELECT * FROM offline_activity WHERE userId = :userId AND loginTime BETWEEN :startMillis AND :endMillis")
    suspend fun getByUserIdAndLoginTimeBetween(userId: String, startMillis: Long, endMillis: Long): List<OfflineActivity>

    @Query("SELECT * FROM offline_activity WHERE _rev IS NULL AND type = 'login'")
    suspend fun getPendingLoginUploads(): List<OfflineActivity>

    @Query("SELECT MAX(loginTime) FROM offline_activity")
    suspend fun getGlobalLastVisit(): Long?

    @Query("SELECT MAX(loginTime) FROM offline_activity WHERE userName = :userName")
    suspend fun getLastVisit(userName: String): Long?

    @Query("SELECT * FROM offline_activity WHERE type = :type ORDER BY loginTime DESC LIMIT 1")
    suspend fun getLatestByType(type: String): OfflineActivity?

    @Query("SELECT * FROM offline_activity WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<OfflineActivity>

    @Query("SELECT * FROM offline_activity WHERE _id IN (:remoteIds)")
    suspend fun getByRemoteIds(remoteIds: List<String>): List<OfflineActivity>

    @Query("SELECT * FROM offline_activity WHERE loginTime IN (:loginTimes) AND userName IN (:userNames)")
    suspend fun getByLoginTimesAndUserNames(loginTimes: List<Long>, userNames: List<String>): List<OfflineActivity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: OfflineActivity)

    @Upsert
    suspend fun upsertAll(activities: List<OfflineActivity>)

    @Query("UPDATE offline_activity SET logoutTime = :logoutTime WHERE id = :id")
    suspend fun updateLogoutTime(id: String, logoutTime: Long): Int
}
