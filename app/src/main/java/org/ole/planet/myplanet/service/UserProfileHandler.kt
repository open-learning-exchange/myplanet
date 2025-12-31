package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.di.ServiceEntryPoint
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmResourceActivity
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Utilities

class UserProfileHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val realmService: DatabaseService,
    @AppPreferences private val settings: SharedPreferences,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val userRepository: UserRepository,
) {
    private val fullName: String

    constructor(context: Context) : this(
        context,
        DatabaseService(context),
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE),
        EntryPointAccessors.fromApplication(
            context.applicationContext, ServiceEntryPoint::class.java
        ).applicationScope(),
        EntryPointAccessors.fromApplication(
            context.applicationContext, ServiceEntryPoint::class.java
        ).userRepository()
    )

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

                realmService.executeTransactionAsync { realm ->
                    val offlineActivities = realm.createObject(RealmOfflineActivity::class.java, UUID.randomUUID().toString())
                    offlineActivities.userId = userId
                    offlineActivities.userName = userName
                    offlineActivities.parentCode = parentCode
                    offlineActivities.createdOn = planetCode
                    offlineActivities.type = KEY_LOGIN
                    offlineActivities._rev = null
                    offlineActivities._id = null
                    offlineActivities.description = "Member login on offline application"
                    offlineActivities.loginTime = Date().time
                }
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
                realmService.executeTransactionAsync { realm ->
                    RealmOfflineActivity.getRecentLogin(realm)
                        ?.logoutTime = Date().time
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    val lastVisit: Long? get() = realmService.withRealm { realm ->
        realm.where(RealmOfflineActivity::class.java).max("loginTime") as Long?
    }
    val offlineVisits: Int get() = getOfflineVisits(userModel)

    fun getOfflineVisits(m: RealmUserModel?): Int {
        return realmService.withRealm { realm ->
            val dbUsers = realm.where(RealmOfflineActivity::class.java)
                .equalTo("userName", m?.name)
                .equalTo("type", KEY_LOGIN)
                .findAll()
            if (!dbUsers.isEmpty()) {
                dbUsers.size
            } else {
                0
            }
        }
    }

    fun getLastVisit(m: RealmUserModel): String {
        return realmService.withRealm { realm ->
            val lastLogoutTimestamp = realm.where(RealmOfflineActivity::class.java)
                .equalTo("userName", m.name)
                .max("loginTime") as Long?
            if (lastLogoutTimestamp != null) {
                val date = Date(lastLogoutTimestamp)
                SimpleDateFormat("MMMM dd, yyyy hh:mm a", Locale.getDefault()).format(date)
            } else {
                "No logout record found"
            }
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

                realmService.executeTransactionAsync { realm ->
                    val offlineActivities = realm.createObject(RealmResourceActivity::class.java, "${UUID.randomUUID()}")
                    offlineActivities.user = userName
                    offlineActivities.parentCode = parentCode
                    offlineActivities.createdOn = planetCode
                    offlineActivities.type = type
                    offlineActivities.title = itemTitle
                    offlineActivities.resourceId = itemResourceId
                    offlineActivities.time = Date().time
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val numberOfResourceOpen: String
        get() = realmService.withRealm { realm ->
            val count = realm.where(RealmResourceActivity::class.java)
                .equalTo("user", fullName)
                .equalTo("type", KEY_RESOURCE_OPEN)
                .count()
            if (count == 0L) "" else "Resource opened $count times."
        }

    suspend fun maxOpenedResource(): String {
        return withContext(Dispatchers.IO) {
            realmService.withRealm { realm ->
                val activities = realm.where(RealmResourceActivity::class.java)
                    .equalTo("user", fullName)
                    .equalTo("type", KEY_RESOURCE_OPEN)
                    .findAll()

                if (activities.isEmpty()) {
                    return@withRealm ""
                }

                val resourceCounts = activities
                    .groupBy { it.resourceId }
                    .mapValues { entry ->
                        val count = entry.value.size
                        val title = entry.value.first().title
                        Pair(count, title)
                    }

                val maxEntry = resourceCounts.maxByOrNull { it.value.first }

                if (maxEntry == null || maxEntry.value.first == 0) {
                    ""
                } else {
                    val maxCount = maxEntry.value.first
                    val title = maxEntry.value.second
                    "$title opened $maxCount times"
                }
            }
        }
    }

    companion object {
        const val KEY_LOGIN = "login"
        const val KEY_RESOURCE_OPEN = "visit"
        const val KEY_RESOURCE_DOWNLOAD = "download"
    }
}
