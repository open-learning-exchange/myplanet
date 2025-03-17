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
    var mRealm: Realm
    private val realmService: DatabaseService
    private val fullName: String

    init {
        try {
            val validContext = context.applicationContext ?: throw IllegalArgumentException("Invalid context provided")
            realmService = DatabaseService(validContext)
            settings = validContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

//    suspend fun onLogin() {
//        withContext(Dispatchers.IO) {
//            val realm = realmService.realmInstance
//            try {
//                val startTransaction = System.currentTimeMillis()
//                realm.executeTransaction { r ->
//                    val startCreateUser = System.currentTimeMillis()
//                    val offlineActivities = r.copyToRealm(createUser(r))
//                    val createUserTime = System.currentTimeMillis() - startCreateUser
//                    Log.d("Performance", "createUser execution time: ${createUserTime}ms")
//
//                    offlineActivities.type = KEY_LOGIN
//                    offlineActivities._rev = null
//                    offlineActivities._id = null
//                    offlineActivities.description = "Member login on offline application"
//                    offlineActivities.loginTime = Date().time
//                }
//                val transactionTime = System.currentTimeMillis() - startTransaction
//                Log.d("Performance", "Realm transaction execution time: ${transactionTime}ms")
//            } catch (e: Exception) {
//                e.printStackTrace()
//            } finally {
//                realm.close()
//            }
//        }
//    }

//    suspend fun onLogin() {
//        withContext(Dispatchers.IO) {
//            val realm = realmService.realmInstance
//            try {
//                val startTransaction = System.currentTimeMillis()
//
//                realm.executeTransaction { r ->
//                    if (r.isClosed) return@executeTransaction
//                    r.compactRealm() // Auto-compact before writing
//
//                    val offlineActivities = createUser(r) // Avoid copyToRealm()
//                    offlineActivities.type = KEY_LOGIN
//                    offlineActivities.loginTime = Date().time
//                }
//
//                val transactionTime = System.currentTimeMillis() - startTransaction
//                Log.d("Performance", "Realm transaction execution time: ${transactionTime}ms")
//            } catch (e: Exception) {
//                e.printStackTrace()
//            } finally {
//                realm.close()
//            }
//        }
//    }

    suspend fun onLogin() {
        withContext(Dispatchers.IO) {
            val realm = realmService.realmInstance
            try {
                val startTransaction = System.currentTimeMillis()

                realm.executeTransactionAsync({ r ->
                    val count = r.where(RealmOfflineActivity::class.java).count()
                    Log.d("Performance", "Total RealmOfflineActivity records: $count")

                    if (count > 100000) {
                        val oldRecords = r.where(RealmOfflineActivity::class.java)
                            .lessThan("loginTime", System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000)
                            .findAll()
                        oldRecords.deleteAllFromRealm()
                        Log.d("Performance", "Deleted old records: ${oldRecords.size}")
                    }

                    val offlineActivities = createUser(r)
                    r.insert(offlineActivities) // Faster than copyToRealm()
                }, {
                    val transactionTime = System.currentTimeMillis() - startTransaction
                    Log.d("Performance", "Realm transaction execution time: ${transactionTime}ms")
                }, { error ->
                    Log.e("Performance", "Transaction failed: ${error.message}")
                })

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                realm.close()
            }
        }
    }

    //hopeful
//    suspend fun onLogin() {
//        withContext(Dispatchers.IO) {
//            val realm = realmService.realmInstance
//            try {
//                val startTransaction = System.currentTimeMillis()
//
//                realm.executeTransaction { r ->
//                    val offlineActivities = createUser(r) // Already managed, no need for copyToRealm()
//
//                    // You can still update these fields inside the transaction
//                    offlineActivities.type = KEY_LOGIN
//                    offlineActivities._rev = null
//                    offlineActivities._id = null
//                    offlineActivities.description = "Member login on offline application"
//                    offlineActivities.loginTime = Date().time
//                }
//
//                val transactionTime = System.currentTimeMillis() - startTransaction
//                Log.d("Performance", "Realm transaction execution time: ${transactionTime}ms")
//            } catch (e: Exception) {
//                e.printStackTrace()
//            } finally {
//                realm.close()
//            }
//        }
//    }

    private fun createUser(realm: Realm): RealmOfflineActivity {
        val startQuery = System.currentTimeMillis()
        val model = realm.where(RealmUserModel::class.java)
            .equalTo("id", settings.getString("userId", ""))
            .findFirst()
        val queryTime = System.currentTimeMillis() - startQuery
        Log.d("Performance", "RealmUserModel query time: ${queryTime}ms")

        val offlineActivities = realm.createObject(RealmOfflineActivity::class.java, UUID.randomUUID().toString())
        offlineActivities.userId = model?.id
        offlineActivities.userName = model?.name
        offlineActivities.parentCode = model?.parentCode
        offlineActivities.createdOn = model?.planetCode
        return offlineActivities
    }

//    suspend fun onLogin() {
//        withContext(Dispatchers.IO) {
//            // Create a new Realm instance in this background thread
//            val realm = realmService.realmInstance
//            try {
//                realm.executeTransaction { r ->
//                    val offlineActivities = r.copyToRealm(createUser(r))
//                    offlineActivities.type = KEY_LOGIN
//                    offlineActivities._rev = null
//                    offlineActivities._id = null
//                    offlineActivities.description = "Member login on offline application"
//                    offlineActivities.loginTime = Date().time
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//            } finally {
//                realm.close()
//            }
//        }
//    }

    suspend fun onLogout() {
        withContext(Dispatchers.IO) {
            val realm = realmService.realmInstance
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

    fun onDestroy() {
        if (!mRealm.isClosed) {
            mRealm.close()
        }
    }

//    private fun createUser(realm: Realm): RealmOfflineActivity {
//        val offlineActivities = realm.createObject(RealmOfflineActivity::class.java, UUID.randomUUID().toString())
//        // Get user model from the passed realm instance
//        val model = realm.where(RealmUserModel::class.java)
//            .equalTo("id", settings.getString("userId", ""))
//            .findFirst()
//
//        offlineActivities.userId = model?.id
//        offlineActivities.userName = model?.name
//        offlineActivities.parentCode = model?.parentCode
//        offlineActivities.createdOn = model?.planetCode
//        return offlineActivities
//    }

    val lastVisit: Long? get() = mRealm.where(RealmOfflineActivity::class.java).max("loginTime") as Long?
    val offlineVisits: Int get() = getOfflineVisits(userModel)

    fun getOfflineVisits(m: RealmUserModel?): Int { val dbUsers = mRealm.where(RealmOfflineActivity::class.java).equalTo("userName", m?.name).equalTo("type", KEY_LOGIN).findAll()
        return if (!dbUsers.isEmpty()) {
            dbUsers.size
        } else {
            0
        }
    }

    fun getLastVisit(m: RealmUserModel): String {
        val realm = Realm.getDefaultInstance()
        val lastLogoutTimestamp = realm.where(RealmOfflineActivity::class.java)
            .equalTo("userName", m.name)
            .max("loginTime") as Long?
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

        if (!mRealm.isInTransaction) mRealm.beginTransaction()
        val offlineActivities = mRealm.copyToRealm(createResourceUser(model))
        offlineActivities.type = type
        offlineActivities.title = item.title
        offlineActivities.resourceId = item.resourceId
        offlineActivities.time = Date().time
        mRealm.commitTransaction()
    }

    private fun createResourceUser(model: RealmUserModel?): RealmResourceActivity {
        val offlineActivities = mRealm.createObject(RealmResourceActivity::class.java, "${UUID.randomUUID()}")
        offlineActivities.user = model?.name
        offlineActivities.parentCode = model?.parentCode
        offlineActivities.createdOn = model?.planetCode
        return offlineActivities
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
