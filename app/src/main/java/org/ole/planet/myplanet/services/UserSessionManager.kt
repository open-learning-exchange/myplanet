package org.ole.planet.myplanet.services

import android.content.Context
import android.util.Log
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.DeviceUserRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.utils.DispatcherProvider

class UserSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPrefManager: SharedPrefManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val userRepository: UserRepository,
    private val activitiesRepository: ActivitiesRepository,
    private val deviceUserRepository: DeviceUserRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val syncManager: Lazy<SyncManager>
) {
    private val fullName: String

    init {
        try {
            fullName = sharedPrefManager.getUserName()
        } catch (e: IllegalArgumentException) {
            throw e
        }
    }

    suspend fun getUserModel(): RealmUser? {
        return userRepository.getUserModelSuspending()
    }

    fun onLogin() {
        onLoginAsync()
    }

    fun onLoginAsync(callback: (() -> Unit)? = null, onError: ((Throwable) -> Unit)? = null) {
        applicationScope.launch(dispatcherProvider.io) {
            try {
                val model = getUserModel()
                activitiesRepository.logLogin(
                    userId = model?.id,
                    userName = model?.name,
                    parentCode = model?.parentCode,
                    planetCode = model?.planetCode
                )
                val isNewDeviceUser = deviceUserRepository.upsertFromLogin(
                    userId = model?.id,
                    userName = model?.name,
                    parentCode = model?.parentCode,
                    planetCode = model?.planetCode
                )
                if (isNewDeviceUser) {
                    triggerDeviceUserCatchUpSync()
                }
                withContext(dispatcherProvider.main) {
                    callback?.invoke()
                }
            } catch (e: Exception) {
                withContext(dispatcherProvider.main) {
                    onError?.invoke(e)
                }
            }
        }
    }

    /**
     * Kicks off a foreground sync the moment a new RealmDeviceUser row appears. Without this,
     * a user logging in for the first time on a tablet (whether via login or signup) would not
     * see their previously-deferred user-bound tables until the next scheduled sync, which can
     * cause duplicate writes (e.g. a team-survey submission written locally because the existing
     * one wasn't pulled). The trigger fires once per new device user; same-user re-login is a no-op.
     */
    private fun triggerDeviceUserCatchUpSync() {
        val listener = object : OnSyncListener {
            override fun onSyncStarted() {
                Log.d(TAG, "device-user catch-up sync started")
            }
            override fun onSyncComplete() {
                Log.d(TAG, "device-user catch-up sync complete")
            }
            override fun onSyncFailed(msg: String?) {
                Log.d(TAG, "device-user catch-up sync failed: $msg")
            }
        }
        syncManager.get().start(listener, "sync")
    }

    fun logoutAsync() {
        applicationScope.launch(dispatcherProvider.io) {
            try {
                val model = getUserModel()
                activitiesRepository.logLogout(model?.name)
            } catch (e: Exception) {
                Log.e(TAG, "Error in logoutAsync", e)
            }
        }
    }

    suspend fun getGlobalLastVisit(): Long? {
        return activitiesRepository.getGlobalLastVisit()
    }

    suspend fun getOfflineVisits(m: RealmUser?): Int {
        return m?.id?.let { activitiesRepository.getOfflineVisitCount(it) } ?: 0
    }

    suspend fun getLastVisit(m: RealmUser): String {
        val lastLogoutTimestamp = activitiesRepository.getLastVisit(m.name ?: "")
        return if (lastLogoutTimestamp != null) {
            val date = Date(lastLogoutTimestamp)
            SimpleDateFormat("MMMM dd, yyyy hh:mm a", Locale.getDefault()).format(date)
        } else {
            "No logout record found"
        }
    }

    fun setResourceOpenCount(item: RealmMyLibrary) {
        setResourceOpenCount(item, KEY_RESOURCE_OPEN)
    }

    fun setResourceOpenCount(item: RealmMyLibrary, type: String?) {
        val itemTitle = item.title
        val itemResourceId = item.resourceId

        applicationScope.launch(dispatcherProvider.io) {
            try {
                val model = getUserModel()
                if (model?.id?.startsWith("guest") == true) {
                    return@launch
                }

                activitiesRepository.logResourceOpen(
                    userName = model?.name,
                    parentCode = model?.parentCode,
                    planetCode = model?.planetCode,
                    title = itemTitle,
                    resourceId = itemResourceId,
                    type = type
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error in setResourceOpenCount", e)
            }
        }
    }

    suspend fun getNumberOfResourceOpen(): String {
        val count = activitiesRepository.getResourceOpenCount(fullName, KEY_RESOURCE_OPEN)
        return if (count == 0L) "" else "Resource opened $count times."
    }

    suspend fun maxOpenedResource(): String {
        val result = activitiesRepository.getMostOpenedResource(fullName, KEY_RESOURCE_OPEN)
        return if (result == null) {
            ""
        } else {
            "${result.first} opened ${result.second} times"
        }
    }

    companion object {
        private const val TAG = "UserSessionManager"
        const val KEY_LOGIN = "login"
        const val KEY_RESOURCE_OPEN = "visit"
        const val KEY_RESOURCE_DOWNLOAD = "download"
    }
}
