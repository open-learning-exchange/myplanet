package org.ole.planet.myplanet.model

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.Serializable
import java.util.Calendar
import java.util.Date
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.VersionUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin

class MyPlanet : Serializable {
    var planetVersion: String? = null
    var minapkcode = 0
    var latestapkcode = 0
    var apkpath: String? = null
    var appname: String? = null
    var localapkpath: String? = null
    override fun toString(): String {
        return appname ?: ""
    }

    companion object {
        fun getMyPlanetActivities(
            context: Context,
            spm: SharedPrefManager,
            model: UserEntity,
            now: Long = System.currentTimeMillis()
        ): JsonObject {
            val postJSON = JsonObject()
            val planet = JsonUtils.gson.fromJson(spm.getVersionDetail() ?: "", MyPlanet::class.java)
            if (planet != null) postJSON.addProperty("planetVersion", planet.planetVersion)
            postJSON.addProperty("_id", VersionUtils.getAndroidId(context) + "@" + NetworkUtils.getUniqueIdentifier())
            postJSON.addProperty("last_synced", spm.getLastSync())
            postJSON.addProperty("parentCode", model.parentCode)
            postJSON.addProperty("createdOn", model.planetCode)
            postJSON.addProperty("type", "usages")
            postJSON.add("usages", getTabletUsages(context, spm, now))
            return postJSON
        }

        fun getNormalMyPlanetActivities(context: Context, spm: SharedPrefManager, model: UserEntity): JsonObject {
            val postJSON = JsonObject()
            val planet = JsonUtils.gson.fromJson(spm.getVersionDetail() ?: "", MyPlanet::class.java)
            if (planet != null) postJSON.addProperty("planetVersion", planet.planetVersion)
            postJSON.addProperty("last_synced", spm.getLastSync())
            postJSON.addProperty("parentCode", model.parentCode)
            postJSON.addProperty("createdOn", model.planetCode)
            postJSON.addProperty("version", VersionUtils.getVersionCode(context))
            postJSON.addProperty("versionName", VersionUtils.getVersionName(context))
            postJSON.addDocumentOrigin()
            postJSON.addProperty("uniqueAndroidId", VersionUtils.getAndroidId(context))
            postJSON.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
            postJSON.addProperty("deviceName", NetworkUtils.getDeviceName())
            postJSON.addProperty("time", Date().time)
            postJSON.addProperty("type", "sync")
            return postJSON
        }

        fun getTabletUsages(
            context: Context,
            spm: SharedPrefManager,
            now: Long = System.currentTimeMillis()
        ): JsonArray {
            val cal = Calendar.getInstance()
            cal.timeInMillis = spm.getLastUsageUploaded()
            val arr = JsonArray()
            val mUsageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val queryUsageStats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, now)
            if (queryUsageStats != null) {
                for (s in queryUsageStats) {
                    addStats(s, arr, context)
                }
            }
            return arr
        }

        private fun addStats(s: UsageStats, arr: JsonArray, context: Context) {
            if (s.packageName == context.packageName) {
                val `object` = JsonObject()
                `object`.addProperty("lastTimeUsed", if (s.lastTimeUsed > 0) s.lastTimeUsed else 0)
                `object`.addProperty("firstTimeUsed", if (s.firstTimeStamp > 0) s.lastTimeStamp else 0)
                `object`.addProperty("totalForegroundTime", s.totalTimeInForeground)
                val totalUsed = s.lastTimeUsed - s.firstTimeStamp
                `object`.addProperty("totalUsed", if (totalUsed > 0) totalUsed else 0)
                `object`.addProperty("version", VersionUtils.getVersionCode(context))
                `object`.addProperty("versionName", VersionUtils.getVersionName(context))
                `object`.addDocumentOrigin()
                `object`.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
                `object`.addProperty("deviceName", NetworkUtils.getDeviceName())
                `object`.addProperty("time", Date().time)
                arr.add(`object`)
            }
        }
    }
}
