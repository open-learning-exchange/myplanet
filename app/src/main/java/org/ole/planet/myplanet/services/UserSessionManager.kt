package org.ole.planet.myplanet.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.SecurePrefs

class UserSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPrefManager: SharedPrefManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val userRepository: UserRepository,
    private val activitiesRepository: ActivitiesRepository,
    private val dispatcherProvider: DispatcherProvider
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

    suspend fun saveUserInfoPref(settings: SharedPreferences, password: String?, user: RealmUser?) {
        withContext(dispatcherProvider.io) {
            SecurePrefs.saveCredentials(context, settings, user?.name, password)
        }
        sharedPrefManager.setUserId(user?.id ?: "")
        sharedPrefManager.setUserName(user?.name ?: "")
        sharedPrefManager.rawPreferences.edit().apply {
            remove("password")
            putString("firstName", user?.firstName)
            putString("lastName", user?.lastName)
            putString("middleName", user?.middleName)
            user?.userAdmin?.let { putBoolean("isUserAdmin", it) }
            putLong("lastLogin", System.currentTimeMillis())
            apply()
        }
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

    companion object {
        private const val TAG = "UserSessionManager"
        const val KEY_LOGIN = "login"
        const val KEY_RESOURCE_OPEN = "visit"
        const val KEY_RESOURCE_DOWNLOAD = "download"
    }
}
