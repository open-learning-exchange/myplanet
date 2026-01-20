package org.ole.planet.myplanet.service

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
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utilities.Utilities

class UserSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
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

    val userModel: RealmUserModel? get() = userRepository.getUserModel()

    fun getUserModelCopy(): RealmUserModel? {
        return userRepository.getUserModel()
    }

    fun onLogin() {
        onLoginAsync()
    }

    fun onLoginAsync(callback: (() -> Unit)? = null, onError: ((Throwable) -> Unit)? = null) {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val model = getUserModelCopy()
                val userId = model?.id
                val userName = model?.name
                val parentCode = model?.parentCode
                val planetCode = model?.planetCode

                activitiesRepository.logLogin(userId, userName, parentCode, planetCode)

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
                activitiesRepository.logLogout()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getGlobalLastVisit(): Long? {
        return activitiesRepository.getGlobalLastVisit()
    }

    suspend fun getOfflineVisits(m: RealmUserModel?): Int {
        return activitiesRepository.getOfflineVisits(m?.name)
    }

    suspend fun getLastVisit(m: RealmUserModel): String {
        val lastLogoutTimestamp = activitiesRepository.getLastVisit(m.name)
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
                val model = getUserModelCopy()
                if (model?.id?.startsWith("guest") == true) {
                    return@launch
                }

                val userName = model?.name
                val parentCode = model?.parentCode
                val planetCode = model?.planetCode

                activitiesRepository.logResourceOpen(userName, parentCode, planetCode, type, itemTitle, itemResourceId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getNumberOfResourceOpen(): String {
        val count = activitiesRepository.getNumberOfResourceOpen(fullName, KEY_RESOURCE_OPEN)
        return if (count == 0L) "" else "Resource opened $count times."
    }

    suspend fun maxOpenedResource(): String {
        val activities = activitiesRepository.getAllResourceActivities(fullName, KEY_RESOURCE_OPEN)

        if (activities.isEmpty()) {
            return ""
        }

        val resourceCounts = activities
            .groupBy { it.resourceId }
            .mapValues { entry ->
                val count = entry.value.size
                val title = entry.value.first().title
                Pair(count, title)
            }

        val maxEntry = resourceCounts.maxByOrNull { it.value.first }

        return if (maxEntry == null || maxEntry.value.first == 0) {
            ""
        } else {
            val maxCount = maxEntry.value.first
            val title = maxEntry.value.second
            "$title opened $maxCount times"
        }
    }

    companion object {
        const val KEY_LOGIN = "login"
        const val KEY_RESOURCE_OPEN = "visit"
        const val KEY_RESOURCE_DOWNLOAD = "download"
    }
}
