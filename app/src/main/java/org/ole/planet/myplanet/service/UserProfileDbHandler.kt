package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmResourceActivity
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Utilities

class UserProfileDbHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val realmService: DatabaseService,
    @AppPreferences private val settings: SharedPreferences
) {
    private val fullName: String

    // Backward compatibility constructor
    constructor(context: Context) : this(
        context,
        DatabaseService(context),
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    init {
        try {
            fullName = Utilities.getUserName(settings)
        } catch (e: IllegalArgumentException) {
            throw e
        }
    }

    val userModel: RealmUserModel? get() = getUserModelCopy()

    fun getUserModelCopy(): RealmUserModel? {
        val userId = settings.getString("userId", null)?.takeUnless { it.isBlank() } ?: return null
        return realmService.withRealm { realm ->
            realm.where(RealmUserModel::class.java)
                .equalTo("id", userId)
                .or()
                .equalTo("_id", userId)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    fun onLogin() {
        onLoginAsync()
    }

    fun onLoginAsync(callback: (() -> Unit)? = null, onError: ((Throwable) -> Unit)? = null) {
        val model = getUserModelCopy()
        val userId = model?.id
        val userName = model?.name
        val parentCode = model?.parentCode
        val planetCode = model?.planetCode

        realmService.withRealmAsync(
            { realm ->
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
            },
            onSuccess = {
                callback?.invoke()
            },
            onError = { error ->
                error.printStackTrace()
                onError?.invoke(error)
            }
        )
    }

    fun logoutAsync() {
        realmService.withRealmAsync(
            { realm ->
                RealmOfflineActivity.getRecentLogin(realm)
                    ?.logoutTime = Date().time
            },
            onError = { error ->
                error.printStackTrace()
            }
        )
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
        val model = getUserModelCopy()
        if (model?.id?.startsWith("guest") == true) {
            return
        }

        val userName = model?.name
        val parentCode = model?.parentCode
        val planetCode = model?.planetCode
        val itemTitle = item.title
        val itemResourceId = item.resourceId

        realmService.withRealmAsync({ realm ->
            val offlineActivities = realm.createObject(RealmResourceActivity::class.java, "${UUID.randomUUID()}")
            offlineActivities.user = userName
            offlineActivities.parentCode = parentCode
            offlineActivities.createdOn = planetCode
            offlineActivities.type = type
            offlineActivities.title = itemTitle
            offlineActivities.resourceId = itemResourceId
            offlineActivities.time = Date().time
        }, onError = { it.printStackTrace() })
    }

    val numberOfResourceOpen: String
        get() = realmService.withRealm { realm ->
            val count = realm.where(RealmResourceActivity::class.java)
                .equalTo("user", fullName)
                .equalTo("type", KEY_RESOURCE_OPEN)
                .count()
            if (count == 0L) "" else "Resource opened $count times."
        }

    val maxOpenedResource: String
        get() = realmService.withRealm { realm ->
            val result = realm.where(RealmResourceActivity::class.java)
                .equalTo("user", fullName)
                .equalTo("type", KEY_RESOURCE_OPEN)
                .findAll()
                .where()
                .distinct("resourceId")
                .findAll()

            var maxCount = 0L
            var maxOpenedResource = ""

            for (realmResourceActivities in result) {
                val count = realm.where(RealmResourceActivity::class.java)
                    .equalTo("user", fullName)
                    .equalTo("type", KEY_RESOURCE_OPEN)
                    .equalTo("resourceId", realmResourceActivities.resourceId)
                    .count()

                if (count > maxCount) {
                    maxCount = count
                    maxOpenedResource = "${realmResourceActivities.title}"
                }
            }

            if (maxCount == 0L) "" else "$maxOpenedResource opened $maxCount times"
        }

    companion object {
        const val KEY_LOGIN = "login"
        const val KEY_RESOURCE_OPEN = "visit"
        const val KEY_RESOURCE_DOWNLOAD = "download"
    }
}
