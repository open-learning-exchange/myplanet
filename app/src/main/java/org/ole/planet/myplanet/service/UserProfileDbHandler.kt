package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmOfflineActivity
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
    private val realmService: DatabaseService
    private val fullName: String

    init {
        try {
            val validContext = context.applicationContext ?: throw IllegalArgumentException("Invalid context provided")
            realmService = DatabaseService(validContext)
            settings = validContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            fullName = Utilities.getUserName(settings)
        } catch (e: IllegalArgumentException) {
            throw e
        }
    }

    private fun getRealm(): Realm {
        return realmService.realmInstance
    }

    val userModel: RealmUserModel? get() {
        val realm = getRealm()
        try {
            return realm.where(RealmUserModel::class.java).equalTo("id", settings.getString("userId", ""))
                .findFirst()?.let { realm.copyFromRealm(it) }
        } finally {
            realm.close()
        }
    }

    suspend fun onLogin() {
        Log.d("LoginFlow", "onLogin: Starting database operations")
        return withContext(Dispatchers.IO) {
            val realm = getRealm()
            try {
                Log.d("LoginFlow", "onLogin: Got realm instance, starting transaction")
                // Execute as a single transaction for better performance
                realm.executeTransaction { r ->
                    // First, check if we need to delete old records at all
                    val oldRecordsCount = r.where(RealmOfflineActivity::class.java)
                        .lessThan("loginTime", System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000)
                        .count()

                    Log.d("LoginFlow", "onLogin: Found $oldRecordsCount old records to delete")

                    if (oldRecordsCount > 0) {
                        // Use more efficient batch deletion by query rather than finding all
                        r.where(RealmOfflineActivity::class.java)
                            .lessThan("loginTime", System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000)
                            .findAll().deleteAllFromRealm()

                        Log.d("LoginFlow", "onLogin: Deleted old records")
                    }

                    // Create just one object (already optimized)
                    val offlineActivity = r.createObject(RealmOfflineActivity::class.java, UUID.randomUUID().toString())
                    offlineActivity.type = KEY_LOGIN
                    offlineActivity.description = "Member login on offline application"
                    offlineActivity.loginTime = System.currentTimeMillis()
                    offlineActivity.userName = Utilities.getUserName(settings)

                    Log.d("LoginFlow", "onLogin: Created new login record")
                }
                Log.d("LoginFlow", "onLogin: Transaction completed successfully")
            } catch (e: Exception) {
                Log.e("LoginFlow", "Error in onLogin: ${e.message}")
                e.printStackTrace()
            } finally {
                realm.close()
                Log.d("LoginFlow", "onLogin: Realm instance closed")
            }
        }
    }

    suspend fun onLogout() {
        withContext(Dispatchers.IO) {
            val realm = getRealm()
            try {
                realm.executeTransaction {
                    val offlineActivities = RealmOfflineActivity.getRecentLogin(it)
                    offlineActivities?.logoutTime = Date().time
                }
            } finally {
                realm.close()
            }
        }
    }

    val lastVisit: Long? get() {
        val realm = getRealm()
        try {
            return realm.where(RealmOfflineActivity::class.java).max("loginTime") as Long?
        } finally {
            realm.close()
        }
    }

    val offlineVisits: Int get() = getOfflineVisits(userModel)

    fun getOfflineVisits(m: RealmUserModel?): Int {
        val realm = getRealm()
        try {
            val dbUsers = realm.where(RealmOfflineActivity::class.java).equalTo("userName", m?.name)
                .equalTo("type", KEY_LOGIN).findAll()
            return if (!dbUsers.isEmpty()) {
                dbUsers.size
            } else {
                0
            }
        } finally {
            realm.close()
        }
    }

    fun getLastVisit(m: RealmUserModel): String {
        val realm = getRealm()
        val lastLogoutTimestamp = realm.where(RealmOfflineActivity::class.java)
            .equalTo("userName", m.name).max("loginTime") as Long?
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
        val model = userModel
        if (model?.id?.startsWith("guest") == true) {
            return
        }

        val realm = getRealm()
        try {
            realm.executeTransaction { r ->
                val offlineActivities = r.copyToRealm(createResourceUser(r, model))
                offlineActivities.type = type
                offlineActivities.title = item.title
                offlineActivities.resourceId = item.resourceId
                offlineActivities.time = Date().time
            }
        } finally {
            realm.close()
        }
    }

    private fun createResourceUser(realm: Realm, model: RealmUserModel?): RealmResourceActivity {
        val offlineActivities = realm.createObject(RealmResourceActivity::class.java, "${UUID.randomUUID()}")
        offlineActivities.user = model?.name
        offlineActivities.parentCode = model?.parentCode
        offlineActivities.createdOn = model?.planetCode
        return offlineActivities
    }

    val numberOfResourceOpen: String get() {
        val realm = getRealm()
        try {
            val count = realm.where(RealmResourceActivity::class.java).equalTo("user", fullName)
                .equalTo("type", KEY_RESOURCE_OPEN).count()
            return if (count == 0L) "" else "Resource opened $count times."
        } finally {
            realm.close()
        }
    }

    val maxOpenedResource: String get() {
        val realm = getRealm()
        try {
            val result = realm.where(RealmResourceActivity::class.java).equalTo("user", fullName)
                .equalTo("type", KEY_RESOURCE_OPEN).findAll().where().distinct("resourceId").findAll()
            var maxCount = 0L
            var maxOpenedResource = ""
            for (realmResourceActivities in result) {
                val count = realm.where(RealmResourceActivity::class.java).equalTo("user", fullName)
                    .equalTo("type", KEY_RESOURCE_OPEN).equalTo("resourceId", realmResourceActivities.resourceId)
                    .count()

                if (count > maxCount) {
                    maxCount = count
                    maxOpenedResource = "${realmResourceActivities.title}"
                }
            }
            return if (maxCount == 0L) "" else "$maxOpenedResource opened $maxCount times"
        } finally {
            realm.close()
        }
    }

    companion object {
        const val KEY_LOGIN = "login"
        const val KEY_RESOURCE_OPEN = "visit"
        const val KEY_RESOURCE_DOWNLOAD = "download"
    }
}
