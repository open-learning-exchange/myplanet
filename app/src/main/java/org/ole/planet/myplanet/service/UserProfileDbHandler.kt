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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    var mRealm: Realm
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
            mRealm = realmService.realmInstance
        } catch (e: IllegalArgumentException) {
            throw e
        }
    }

    val userModel: RealmUserModel? get() {
        if (mRealm.isClosed) {
            mRealm = realmService.realmInstance
        }
        return mRealm.where(RealmUserModel::class.java)
            .equalTo("id", settings.getString("userId", ""))
            .findFirst()
    }

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
        if (mRealm.isClosed) {
            mRealm = realmService.realmInstance
        }

        val model = userModel
        val userId = model?.id
        val userName = model?.name
        val parentCode = model?.parentCode
        val planetCode = model?.planetCode

        mRealm.executeTransactionAsync(
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
            {
                callback?.invoke()
            },
            { error ->
                error.printStackTrace()
                onError?.invoke(error)
            }
        )
    }

    fun logoutAsync() {
        val realm = realmService.realmInstance
        realm.executeTransactionAsync(
            { r ->
                RealmOfflineActivity.getRecentLogin(r)
                    ?.logoutTime = Date().time
            },
            {
                realm.close()
            },
            { error ->
                realm.close()
                error.printStackTrace()
            }
        )
    }

    fun onDestroy() {
        if (!mRealm.isClosed) {
            mRealm.close()
        }
    }

    val lastVisit: Long? get() = mRealm.where(RealmOfflineActivity::class.java).max("loginTime") as Long?
    val offlineVisits: Int get() = getOfflineVisits(userModel)

    suspend fun getOfflineVisitsSuspending(): Int = withContext(Dispatchers.IO) {
        realmService.withRealm { realm ->
            val userId = settings.getString("userId", "")
            val user = realm.where(RealmUserModel::class.java)
                .equalTo("id", userId)
                .findFirst()
            val dbUsers = realm.where(RealmOfflineActivity::class.java)
                .equalTo("userName", user?.name)
                .equalTo("type", KEY_LOGIN)
                .findAll()
            dbUsers.size
        }
    }

    fun getOfflineVisits(m: RealmUserModel?): Int { val dbUsers = mRealm.where(RealmOfflineActivity::class.java).equalTo("userName", m?.name).equalTo("type", KEY_LOGIN).findAll()
        return if (!dbUsers.isEmpty()) {
            dbUsers.size
        } else {
            0
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
        val model = userModel
        if (model?.id?.startsWith("guest") == true) {
            return
        }

        val userName = model?.name
        val parentCode = model?.parentCode
        val planetCode = model?.planetCode
        val itemTitle = item.title
        val itemResourceId = item.resourceId

        mRealm.executeTransactionAsync { realm ->
            val offlineActivities = realm.createObject(RealmResourceActivity::class.java, "${UUID.randomUUID()}")
            offlineActivities.user = userName
            offlineActivities.parentCode = parentCode
            offlineActivities.createdOn = planetCode
            offlineActivities.type = type
            offlineActivities.title = itemTitle
            offlineActivities.resourceId = itemResourceId
            offlineActivities.time = Date().time
        }
    }

    val numberOfResourceOpen: String get() {
        val count = mRealm.where(RealmResourceActivity::class.java).equalTo("user", fullName)
            .equalTo("type", KEY_RESOURCE_OPEN).count()
        return if (count == 0L) "" else "Resource opened $count times."
    }

    val maxOpenedResource: String get() {
        val result = mRealm.where(RealmResourceActivity::class.java)
            .equalTo("user", fullName).equalTo("type", KEY_RESOURCE_OPEN)
            .findAll().where().distinct("resourceId").findAll()
        var maxCount = 0L
        var maxOpenedResource = ""
        for (realmResourceActivities in result) {
            val count = mRealm.where(RealmResourceActivity::class.java)
                .equalTo("user", fullName)
                .equalTo("type", KEY_RESOURCE_OPEN)
                .equalTo("resourceId", realmResourceActivities.resourceId).count()

            if (count > maxCount) {
                maxCount = count
                maxOpenedResource = "${realmResourceActivities.title}"
            }
        }
        return if (maxCount == 0L) "" else "$maxOpenedResource opened $maxCount times"
    }

    companion object {
        const val KEY_LOGIN = "login"
        const val KEY_RESOURCE_OPEN = "visit"
        const val KEY_RESOURCE_DOWNLOAD = "download"
    }
}
