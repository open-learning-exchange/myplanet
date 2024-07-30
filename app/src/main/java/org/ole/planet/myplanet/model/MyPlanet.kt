package org.ole.planet.myplanet.model

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.VersionUtils
import java.io.Serializable
import java.util.Calendar
import java.util.Date

class MyPlanet : Serializable {
    @JvmField
    var planetVersion: String? = null
//    @JvmField
//    var latestapk: String? = null
//    @JvmField
//    var minapk: String? = null
    @JvmField
    var minapkcode = 0
    @JvmField
    var latestapkcode = 0
    @JvmField
    var apkpath: String? = null
    @JvmField
    var appname: String? = null
    @JvmField
    var localapkpath: String? = null
    override fun toString(): String {
        return appname!!
    }

    companion object {
        @JvmStatic
        fun getMyPlanetActivities(context: Context, pref: SharedPreferences, model: RealmUserModel): JsonObject {
            val postJSON = JsonObject()
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val planet = Gson().fromJson(preferences.getString("versionDetail", ""), MyPlanet::class.java)
            if (planet != null) postJSON.addProperty("planetVersion", planet.planetVersion)
            postJSON.addProperty("_id", VersionUtils.getAndroidId(MainApplication.context) + "@" + NetworkUtils.getUniqueIdentifier())
            postJSON.addProperty("last_synced", pref.getLong("LastSync", 0))
            postJSON.addProperty("parentCode", model.parentCode)
            postJSON.addProperty("createdOn", model.planetCode)
            postJSON.addProperty("type", "usages")
            postJSON.add("usages", getTabletUsages(context))
            return postJSON
        }

        @JvmStatic
        fun getNormalMyPlanetActivities(context: Context, pref: SharedPreferences, model: RealmUserModel): JsonObject {
            val postJSON = JsonObject()
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val planet = Gson().fromJson(preferences.getString("versionDetail", ""), MyPlanet::class.java)
            if (planet != null) postJSON.addProperty("planetVersion", planet.planetVersion)
            postJSON.addProperty("last_synced", pref.getLong("LastSync", 0))
            postJSON.addProperty("parentCode", model.parentCode)
            postJSON.addProperty("createdOn", model.planetCode)
            postJSON.addProperty("version", VersionUtils.getVersionCode(context))
            postJSON.addProperty("versionName", VersionUtils.getVersionName(context))
            postJSON.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            postJSON.addProperty("uniqueAndroidId", VersionUtils.getAndroidId(MainApplication.context))
            postJSON.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
            postJSON.addProperty("deviceName", NetworkUtils.getDeviceName())
            postJSON.addProperty("time", Date().time)
            postJSON.addProperty("type", "sync")
            return postJSON
        }

        @JvmStatic
        fun getTabletUsages(context: Context): JsonArray {
            val cal = Calendar.getInstance()
            val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            cal.timeInMillis = settings.getLong("lastUsageUploaded", 0)
            val arr = JsonArray()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val mUsageStatsManager = MainApplication.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val queryUsageStats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
                for (s in queryUsageStats) {
                    addStats(s, arr, context)
                }
            }
            return arr
        }

        private fun addStats(s: UsageStats, arr: JsonArray, context: Context) {
            if (s.packageName == MainApplication.context.packageName) {
                val `object` = JsonObject()
                `object`.addProperty("lastTimeUsed", if (s.lastTimeUsed > 0) s.lastTimeUsed else 0)
                `object`.addProperty("firstTimeUsed", if (s.firstTimeStamp > 0) s.lastTimeStamp else 0)
                `object`.addProperty("totalForegroundTime", s.totalTimeInForeground)
                val totalUsed = s.lastTimeUsed - s.firstTimeStamp
                `object`.addProperty("totalUsed", if (totalUsed > 0) totalUsed else 0)
                `object`.addProperty("version", VersionUtils.getVersionCode(context))
                `object`.addProperty("versionName", VersionUtils.getVersionName(context))
                `object`.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
                `object`.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
                `object`.addProperty("deviceName", NetworkUtils.getDeviceName())
                `object`.addProperty("time", Date().time)
                arr.add(`object`)
            }
        }
    }
}