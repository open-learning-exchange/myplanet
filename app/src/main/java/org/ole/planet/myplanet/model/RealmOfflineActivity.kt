package org.ole.planet.myplanet.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import io.realm.kotlin.Realm
import com.google.gson.JsonObject
import java.io.File
import java.io.FileWriter
import java.io.IOException
import com.opencsv.CSVWriter
import android.content.Context
import io.realm.kotlin.query.Sort
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.service.UserProfileDbHandler

class RealmOfflineActivity : RealmObject {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var _rev: String? = null
    var userName: String? = null
    var userId: String? = null
    var type: String? = null
    var description: String? = null
    var createdOn: String? = null
    var parentCode: String? = null
    var loginTime: Long = 0L
    var logoutTime: Long? = null
    var androidId: String? = null

    constructor()

    fun changeRev(r: JsonObject?) {
        if (r != null) {
            _rev = JsonUtils.getString("_rev", r)
            _id = JsonUtils.getString("_id", r)
        }
    }

    companion object {
        private val offlineDataList: MutableList<Array<String>> = mutableListOf()

        fun serializeLoginActivities(realmOfflineActivities: RealmOfflineActivity, context: Context): JsonObject {
            val ob = JsonObject()
            ob.addProperty("user", realmOfflineActivities.userName)
            ob.addProperty("type", realmOfflineActivities.type)
            ob.addProperty("loginTime", realmOfflineActivities.loginTime)
            ob.addProperty("logoutTime", realmOfflineActivities.logoutTime)
            ob.addProperty("createdOn", realmOfflineActivities.createdOn)
            ob.addProperty("parentCode", realmOfflineActivities.parentCode)
            ob.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            ob.addProperty("deviceName", NetworkUtils.getDeviceName())
            ob.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))

            realmOfflineActivities._id?.let {
                ob.addProperty("_id", realmOfflineActivities.logoutTime)
            }
            realmOfflineActivities._rev?.let {
                ob.addProperty("_rev", realmOfflineActivities._rev)
            }
            return ob
        }

        fun getRecentLogin(realm: Realm): RealmOfflineActivity? {
            return realm.query(RealmOfflineActivity::class)
                .query("type == $0", UserProfileDbHandler.KEY_LOGIN)
                .sort("loginTime", Sort.DESCENDING)
                .first()
                .find()
        }

        suspend fun insert(realm: Realm, act: JsonObject?) {
            realm.write {
                val _id = JsonUtils.getString("_id", act)

                val activities = query(RealmOfflineActivity::class)
                    .query("_id == $0", _id)
                    .first()
                    .find() ?: copyToRealm(RealmOfflineActivity().apply {
                        id = _id
                        this._id = _id
                    })

                activities.apply {
                    _rev = JsonUtils.getString("_rev", act)
                    loginTime = JsonUtils.getLong("loginTime", act)
                    type = JsonUtils.getString("type", act)
                    userName = JsonUtils.getString("user", act)
                    parentCode = JsonUtils.getString("parentCode", act)
                    createdOn = JsonUtils.getString("createdOn", act)
                    logoutTime = JsonUtils.getLong("logoutTime", act)
                    androidId = JsonUtils.getString("androidId", act)
                }

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
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                CSVWriter(FileWriter(file)).use { writer ->
                    writer.writeNext(arrayOf("id", "_rev", "userName", "type", "createdOn", "parentCode", "loginTime", "logoutTime", "androidId"))
                    for (row in data) {
                        writer.writeNext(row)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun offlineWriteCsv() {
            writeCsv("${MainApplication.context.getExternalFilesDir(null)}/ole/offlineActivity.csv", offlineDataList)
        }
    }
}