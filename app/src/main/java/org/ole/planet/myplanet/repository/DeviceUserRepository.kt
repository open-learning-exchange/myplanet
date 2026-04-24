package org.ole.planet.myplanet.repository

interface DeviceUserRepository {
    suspend fun upsertFromLogin(
        userId: String?,
        userName: String?,
        parentCode: String?,
        planetCode: String?,
        loginTime: Long = System.currentTimeMillis()
    )

    suspend fun ensureInitializedFromLoginHistory(): Int
    suspend fun hasDeviceUsers(): Boolean
    suspend fun getDeviceUserIds(): List<String>
    suspend fun getDeviceUserNames(): List<String>
}
