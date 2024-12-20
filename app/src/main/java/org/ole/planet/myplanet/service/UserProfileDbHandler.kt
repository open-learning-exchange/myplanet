package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import io.realm.kotlin.Realm
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmOfflineActivity.Companion.getRecentLogin
import org.ole.planet.myplanet.model.RealmResourceActivity
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Utilities
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class UserProfileDbHandler(context: Context) {
    private val settings: SharedPreferences
    var mRealm: Realm
    private val realmService: DatabaseService
    private val fullName: String
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        try {
            val validContext = context.applicationContext ?: throw IllegalArgumentException("Invalid context provided")
            realmService = DatabaseService()
            settings = validContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            fullName = Utilities.getUserName(settings)
            mRealm = realmService.realmInstance
        } catch (e: IllegalArgumentException) {
            throw e
        }
    }

    val userModel: RealmUserModel?
        get() {
            if (mRealm.isClosed()) {
                return realmService.realmInstance.query<RealmUserModel>(RealmUserModel::class).query("id == $0", settings.getString("userId", "")).first().find()
            }
            return mRealm.query<RealmUserModel>(RealmUserModel::class).query("id == $0", settings.getString("userId", "")).first().find()
        }

    fun onLogin() {
        scope.launch {
            mRealm.write {
                val offlineActivities = createUser()
                copyToRealm(offlineActivities).apply {
                    type = KEY_LOGIN
                    _rev = null
                    _id = null
                    description = "Member login on offline application"
                    loginTime = Date().time
                }
            }
        }
    }

    suspend fun onLogout() {
        withContext(Dispatchers.IO) {
            mRealm.write {
                val offlineActivities = getRecentLogin(mRealm)
                offlineActivities?.let { activity ->
                    findLatest(activity)?.apply {
                        logoutTime = Date().time
                    }
                }
            }
        }
    }

    fun onDestroy() {
        if (!mRealm.isClosed()) {
            mRealm.close()
        }
    }

    private fun createUser(): RealmOfflineActivity {
        return RealmOfflineActivity().apply {
            this._id = UUID.randomUUID().toString()
            this.userId = userModel?.id
            this.userName = userModel?.name
            this.parentCode = userModel?.parentCode
            this.createdOn = userModel?.planetCode
        }
    }

    val lastVisit: Long?
        get() = mRealm.query<RealmOfflineActivity>(RealmOfflineActivity::class).sort("loginTime", Sort.DESCENDING).first().find()?.loginTime
    val offlineVisits: Int get() = getOfflineVisits(userModel)

    fun getOfflineVisits(m: RealmUserModel?): Int {
        return mRealm.query<RealmOfflineActivity>(RealmOfflineActivity::class, "userName == $0 AND type == $1", m?.name, KEY_LOGIN).count().find().toInt()
    }

    fun getLastVisit(user: RealmUserModel): String {
        return try {
            val lastLogoutTimestamp = mRealm.query<RealmOfflineActivity>(RealmOfflineActivity::class, "userName == $0", user.name)
                .sort("loginTime", Sort.DESCENDING).first().find()?.loginTime

            if (lastLogoutTimestamp != null) {
                val date = Date(lastLogoutTimestamp)
                SimpleDateFormat("MMMM dd, yyyy hh:mm a", Locale.getDefault()).format(date)
            } else {
                "No logout record found"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "No logout record found"
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

        scope.launch {
            mRealm.write {
                val offlineActivities = createResourceUser(model)
                copyToRealm(offlineActivities).apply {
                    this.type = type
                    this.title = item.title
                    this.resourceId = item.resourceId
                    this.time = Date().time
                }
            }
        }
    }

    private fun createResourceUser(model: RealmUserModel?): RealmResourceActivity {
        return RealmResourceActivity().apply {
            this._id = UUID.randomUUID().toString()
            this.user = model?.name
            this.parentCode = model?.parentCode
            this.createdOn = model?.planetCode
        }
    }

    val numberOfResourceOpen: String
        get() {
            val count = runBlocking {
                mRealm.query<RealmResourceActivity>(RealmResourceActivity::class, "user == $0 AND type == $1", fullName, KEY_RESOURCE_OPEN).count().find()
            }
            return if (count == 0L) "" else "Resource opened $count times."
        }

    val maxOpenedResource: String
        get() {
            var maxCount = 0L
            var maxOpenedResource = ""

            runBlocking {
                val result = mRealm.query<RealmResourceActivity>(RealmResourceActivity::class, "user == $0 AND type == $1", fullName, KEY_RESOURCE_OPEN).distinct("resourceId").find()

                for (realmResourceActivities in result) {
                    val count = mRealm.query<RealmResourceActivity>(RealmResourceActivity::class, "user == $0 AND type == $1 AND resourceId == $2", fullName, KEY_RESOURCE_OPEN, realmResourceActivities.resourceId).count().find()

                    if (count > maxCount) {
                        maxCount = count
                        maxOpenedResource = "${realmResourceActivities.title}"
                    }
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
