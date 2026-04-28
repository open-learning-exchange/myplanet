package org.ole.planet.myplanet.repository

interface DeviceUserRepository {
    /**
     * Records a successful login for a device user. Returns `true` when this user_id had no
     * `RealmDeviceUser` row before — the caller can use that signal to trigger a one-shot
     * catch-up sync so the new user's previously-deferred user-bound tables get fetched.
     */
    suspend fun upsertFromLogin(
        userId: String?,
        userName: String?,
        parentCode: String?,
        planetCode: String?,
        loginTime: Long = System.currentTimeMillis()
    ): Boolean

    suspend fun ensureInitializedFromLoginHistory(): Int
    suspend fun hasDeviceUsers(): Boolean
    suspend fun getDeviceUserIds(): List<String>
    suspend fun getDeviceUserNames(): List<String>
}
