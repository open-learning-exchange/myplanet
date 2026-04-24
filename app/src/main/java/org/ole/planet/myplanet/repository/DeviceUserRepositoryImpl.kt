package org.ole.planet.myplanet.repository

import io.realm.Realm
import java.util.LinkedHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.RealmDeviceUser
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.services.UserSessionManager

class DeviceUserRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher
) : RealmRepository(databaseService, realmDispatcher), DeviceUserRepository {

    override suspend fun upsertFromLogin(
        userId: String?,
        userName: String?,
        parentCode: String?,
        planetCode: String?,
        loginTime: Long
    ) {
        if (userId.isNullOrBlank() || userId.startsWith("guest")) {
            return
        }

        executeTransaction { realm ->
            upsertDeviceUser(
                realm = realm,
                userId = userId,
                userName = userName,
                parentCode = parentCode,
                planetCode = planetCode,
                loginTime = loginTime
            )
        }
    }

    override suspend fun ensureInitializedFromLoginHistory(): Int {
        val existingCount = count(RealmDeviceUser::class.java).toInt()
        if (existingCount > 0) {
            return existingCount
        }

        val loginActivities = queryList(RealmOfflineActivity::class.java) {
            equalTo("type", UserSessionManager.KEY_LOGIN)
            isNotEmpty("userId")
            not().beginsWith("userId", "guest")
        }

        if (loginActivities.isEmpty()) {
            return 0
        }

        val latestLogins = LinkedHashMap<String, RealmOfflineActivity>()
        loginActivities.forEach { activity ->
            val deviceUserId = activity.userId ?: return@forEach
            val existing = latestLogins[deviceUserId]
            val activityTime = activity.loginTime ?: 0L
            val existingTime = existing?.loginTime ?: 0L
            if (existing == null || activityTime >= existingTime) {
                latestLogins[deviceUserId] = activity
            }
        }

        if (latestLogins.isEmpty()) {
            return 0
        }

        executeTransaction { realm ->
            latestLogins.values.forEach { activity ->
                upsertDeviceUser(
                    realm = realm,
                    userId = activity.userId ?: return@forEach,
                    userName = activity.userName,
                    parentCode = activity.parentCode,
                    planetCode = activity.createdOn,
                    loginTime = activity.loginTime ?: System.currentTimeMillis()
                )
            }
        }

        return count(RealmDeviceUser::class.java).toInt()
    }

    override suspend fun hasDeviceUsers(): Boolean {
        return ensureInitializedFromLoginHistory() > 0
    }

    override suspend fun getDeviceUserIds(): List<String> {
        return queryList(RealmDeviceUser::class.java) {
            isNotEmpty("userId")
        }.mapNotNull { it.userId?.takeIf(String::isNotBlank) }
    }

    override suspend fun getDeviceUserNames(): List<String> {
        return queryList(RealmDeviceUser::class.java) {
            isNotEmpty("userName")
        }.mapNotNull { it.userName?.takeIf(String::isNotBlank) }
    }

    private fun upsertDeviceUser(
        realm: Realm,
        userId: String,
        userName: String?,
        parentCode: String?,
        planetCode: String?,
        loginTime: Long
    ) {
        val deviceUser = realm.where(RealmDeviceUser::class.java)
            .equalTo("userId", userId)
            .findFirst()
            ?: realm.createObject(RealmDeviceUser::class.java, userId).apply {
                firstLoginAt = loginTime
            }

        if (deviceUser.firstLoginAt == 0L || loginTime < deviceUser.firstLoginAt) {
            deviceUser.firstLoginAt = loginTime
        }
        if (loginTime >= deviceUser.lastLoginAt) {
            deviceUser.lastLoginAt = loginTime
        }
        if (!userName.isNullOrBlank()) {
            deviceUser.userName = userName
        }
        if (!parentCode.isNullOrBlank()) {
            deviceUser.parentCode = parentCode
        }
        if (!planetCode.isNullOrBlank()) {
            deviceUser.planetCode = planetCode
        }
    }
}
