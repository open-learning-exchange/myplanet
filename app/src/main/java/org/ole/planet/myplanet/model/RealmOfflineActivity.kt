package org.ole.planet.myplanet.model

import android.content.Context
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.Realm
import io.realm.RealmObject
import io.realm.Sort
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.model.RealmNews.Companion.newsDataList
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException

open class RealmOfflineActivity : RealmObject() {
    @PrimaryKey
    var id: String? = null
    @JvmField
    var _id: String? = null
    @JvmField
    var _rev: String? = null
    @JvmField
    var userName: String? = null
    @JvmField
    var userId: String? = null
    @JvmField
    var type: String? = null
    @JvmField
    var description: String? = null
    @JvmField
    var createdOn: String? = null
    @JvmField
    var parentCode: String? = null
    @JvmField
    var loginTime: Long? = null
    @JvmField
    var logoutTime: Long? = null
    @JvmField
    var androidId: String? = null
    fun changeRev(r: JsonObject?) {
        if (r != null) {
            _rev = JsonUtils.getString("_rev", r)
            _id = JsonUtils.getString("_id", r)
        }
    }

    companion object {
        val offlineDataList: MutableList<Array<String>> = mutableListOf()
        @JvmStatic
        fun serializeLoginActivities(realm_offlineActivities: RealmOfflineActivity, context: Context): JsonObject {
            val ob = JsonObject()
            ob.addProperty("user", realm_offlineActivities.userName)
            ob.addProperty("type", realm_offlineActivities.type)
            ob.addProperty("loginTime", realm_offlineActivities.loginTime)
            ob.addProperty("logoutTime", realm_offlineActivities.logoutTime)
            ob.addProperty("createdOn", realm_offlineActivities.createdOn)
            ob.addProperty("parentCode", realm_offlineActivities.parentCode)
            ob.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            ob.addProperty("deviceName", NetworkUtils.getDeviceName())
            ob.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
            if (realm_offlineActivities._id != null) {
                ob.addProperty("_id", realm_offlineActivities.logoutTime)
            }
            if (realm_offlineActivities._rev != null) {
                ob.addProperty("_rev", realm_offlineActivities._rev)
            }
            return ob
        }

        @JvmStatic
        fun getRecentLogin(mRealm: Realm): RealmOfflineActivity? {
            return mRealm.where(RealmOfflineActivity::class.java)
                .equalTo("type", UserProfileDbHandler.KEY_LOGIN).sort("loginTime", Sort.DESCENDING)
                .findFirst()
        }

        @JvmStatic
        fun insert(mRealm: Realm, act: JsonObject?) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            var activities = mRealm.where(RealmOfflineActivity::class.java)
                .equalTo("_id", JsonUtils.getString("_id", act))
                .findFirst()
            if (activities == null) {
                activities = mRealm.createObject(RealmOfflineActivity::class.java, JsonUtils.getString("_id", act))
            }
            if (activities != null) {
                activities._rev = JsonUtils.getString("_rev", act)
                activities._id = JsonUtils.getString("_id", act)
                activities.loginTime = JsonUtils.getLong("loginTime", act)
                activities.type = JsonUtils.getString("type", act)
                activities.userName = JsonUtils.getString("user", act)
                activities.parentCode = JsonUtils.getString("parentCode", act)
                activities.createdOn = JsonUtils.getString("createdOn", act)
                activities.userName = JsonUtils.getString("user", act)
                activities.logoutTime = JsonUtils.getLong("logoutTime", act)
                activities.androidId = JsonUtils.getString("androidId", act)
            }
            mRealm.commitTransaction()

            val csvRow = arrayOf(
                JsonUtils.getString("_id", act),
                JsonUtils.getString("_rev", act),
                JsonUtils.getString("user", act),
                JsonUtils.getString("type", act),
                JsonUtils.getString("createdOn", act),
                JsonUtils.getString("parentCode", act),
                JsonUtils.getLong("loginTime", act).toString(),
                JsonUtils.getLong("logoutTime", act).toString(),
                JsonUtils.getString("androidId", act)
            )
            offlineDataList.add(csvRow)
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(arrayOf("id", "_rev", "userName", "type", "createdOn", "parentCode", "loginTime", "logoutTime", "androidId"))
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun offlineWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/offlineActivity.csv", offlineDataList)
        }
    }
}
