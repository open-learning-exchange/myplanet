package org.ole.planet.myplanet.model

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.Serializable
import java.util.Calendar
import java.util.Date
import kotlinx.serialization.Serializable as KSerializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.VersionUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

@KSerializable
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
            val planet = JsonUtils.gson.fromJson(spm.getVersionDetail() ?: "", MyPlanet::class.java)
            val usages = getTabletUsages(context, spm, now)
            return buildJsonObject {
                if (planet != null) put("planetVersion", planet.planetVersion)
                put("_id", VersionUtils.getAndroidId(context) + "@" + NetworkUtils.getUniqueIdentifier())
                put("last_synced", spm.getLastSync())
                put("parentCode", model.parentCode)
                put("createdOn", model.planetCode)
                put("type", "usages")
                put("usages", usages.toKotlinx())
            }.toGson()
        }

        fun getNormalMyPlanetActivities(context: Context, spm: SharedPrefManager, model: UserEntity): JsonObject {
            val planet = JsonUtils.gson.fromJson(spm.getVersionDetail() ?: "", MyPlanet::class.java)
            val postJSON = buildJsonObject {
                if (planet != null) put("planetVersion", planet.planetVersion)
                put("last_synced", spm.getLastSync())
                put("parentCode", model.parentCode)
                put("createdOn", model.planetCode)
                put("version", VersionUtils.getVersionCode(context))
                put("versionName", VersionUtils.getVersionName(context))
                put("uniqueAndroidId", VersionUtils.getAndroidId(context))
                put("customDeviceName", NetworkUtils.getCustomDeviceName(context))
                put("deviceName", NetworkUtils.getDeviceName())
                put("time", Date().time)
                put("type", "sync")
            }.toGson()
            postJSON.addDocumentOrigin()
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
                val packageName = context.packageName
                val customDeviceName = NetworkUtils.getCustomDeviceName(context)
                val deviceName = NetworkUtils.getDeviceName()
                for (s in queryUsageStats) {
                    addStats(s, arr, context, packageName, customDeviceName, deviceName, now)
                }
            }
            return arr
        }

        private fun addStats(
            s: UsageStats,
            arr: JsonArray,
            context: Context,
            packageName: String,
            customDeviceName: String,
            deviceName: String,
            now: Long
        ) {
            if (s.packageName == packageName) {
                val totalUsed = s.lastTimeUsed - s.firstTimeStamp
                val `object` = buildJsonObject {
                    put("lastTimeUsed", if (s.lastTimeUsed > 0) s.lastTimeUsed else 0)
                    put("firstTimeUsed", if (s.firstTimeStamp > 0) s.lastTimeStamp else 0)
                    put("totalForegroundTime", s.totalTimeInForeground)
                    put("totalUsed", if (totalUsed > 0) totalUsed else 0)
                    put("version", VersionUtils.getVersionCode(context))
                    put("versionName", VersionUtils.getVersionName(context))
                    put("customDeviceName", customDeviceName)
                    put("deviceName", deviceName)
                    put("time", now)
                }.toGson()
                `object`.addDocumentOrigin()
                arr.add(`object`)
            }
        }
    }
}
