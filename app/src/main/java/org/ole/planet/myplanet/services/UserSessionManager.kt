package org.ole.planet.myplanet.services

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.Utilities

class UserSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val realmService: DatabaseService,
    @AppPreferences private val settings: SharedPreferences,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val userRepository: UserRepository,
    private val activitiesRepository: ActivitiesRepository
) {
    private val fullName: String

    init {
        try {
            fullName = Utilities.getUserName(settings)
        } catch (e: IllegalArgumentException) {
            throw e
        }
    }

    @Deprecated("Use getUserModel() suspend function instead")
    val userModel: RealmUser? get() = userRepository.getUserModel()

    @Deprecated("Use getUserModel() suspend function instead")
    fun getUserModelCopy(): RealmUser? {
        return userRepository.getUserModel()
    }

    suspend fun getUserModel(): RealmUser? {
        return userRepository.getUserModelSuspending()
    }

    suspend fun getUserModelSuspending(): RealmUser? {
        return userRepository.getUserModelSuspending()
    }

    fun onLogin() {
        onLoginAsync()
    }

    fun onLoginAsync(callback: (() -> Unit)? = null, onError: ((Throwable) -> Unit)? = null) {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val model = getUserModel()
                activitiesRepository.logLogin(
                    userId = model?.id,
                    userName = model?.name,
                    parentCode = model?.parentCode,
                    planetCode = model?.planetCode
                )
                withContext(Dispatchers.Main) {
                    callback?.invoke()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError?.invoke(e)
                }
            }
        }
    }

    fun logoutAsync() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val model = getUserModel()
                activitiesRepository.logLogout(model?.name)
            } catch (e: Exception) {
                e.printStackTrace()
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

        applicationScope.launch(Dispatchers.IO) {
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
                e.printStackTrace()
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
        const val KEY_LOGIN = "login"
        const val KEY_RESOURCE_OPEN = "visit"
        const val KEY_RESOURCE_DOWNLOAD = "download"
    }
}
