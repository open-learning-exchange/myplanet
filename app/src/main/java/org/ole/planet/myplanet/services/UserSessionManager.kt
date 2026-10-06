package org.ole.planet.myplanet.services

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.AppLog
import org.ole.planet.myplanet.utils.CredentialStore
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TimeProvider

class UserSessionManager @Inject constructor(
    private val credentialStore: CredentialStore,
    private val sharedPrefManager: SharedPrefManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val userRepository: UserRepository,
    private val activitiesRepository: ActivitiesRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val timeProvider: TimeProvider
) {
    suspend fun getUserModel(): UserEntity? {
        return userRepository.getUserModel()
    }

    suspend fun saveUserInfoPref(password: String?, user: UserEntity?) {
        withContext(dispatcherProvider.io) {
            credentialStore.saveCredentials(user?.name, password)
        }
        sharedPrefManager.saveUserInfo(
            userId = user?.id ?: "",
            userName = user?.name ?: "",
            firstName = user?.firstName,
            lastName = user?.lastName,
            middleName = user?.middleName,
            isUserAdmin = user?.userAdmin,
            lastLogin = timeProvider.now()
        )
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
                callback?.invoke()
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }
    }

    fun logoutAsync() {
        applicationScope.launch(dispatcherProvider.io) {
            try {
                val model = getUserModel()
                activitiesRepository.logLogout(model?.name)
            } catch (e: Exception) {
                AppLog.e(TAG, "Error in logoutAsync", e)
            }
        }
    }

    fun setResourceOpenCount(item: MyLibrary) {
        setResourceOpenCount(item, KEY_RESOURCE_OPEN)
    }

    fun setResourceOpenCount(item: MyLibrary, type: String?) {
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
                AppLog.e(TAG, "Error in setResourceOpenCount", e)
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
