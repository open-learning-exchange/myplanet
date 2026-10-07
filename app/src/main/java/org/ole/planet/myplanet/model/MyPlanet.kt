package org.ole.planet.myplanet.model

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import java.io.Serializable
import java.util.Calendar
import java.util.Date
import kotlinx.serialization.Serializable as KSerializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.KJson
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.VersionUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin

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

    /** The cached version detail text, byte for byte `Gson().toJson(this)`: fields in order, `null`s dropped, HTML-safe. */
    fun toVersionDetail(): String = KJson.gsonToJson(
        buildJsonObject {
            put("planetVersion", planetVersion)
            put("minapkcode", minapkcode)
            put("latestapkcode", latestapkcode)
            put("apkpath", apkpath)
            put("appname", appname)
            put("localapkpath", localapkpath)
        }
    )

    companion object {
        /**
         * Reads the cached version detail ([toVersionDetail]'s text, written by Gson before).
         * Like Gson's `fromJson(text, MyPlanet::class.java)`, blank text or JSON `null` gives
         * `null`; malformed text throws.
         */
        fun fromVersionDetail(text: String?): MyPlanet? {
            val element = KJson.parseOrNull(text) ?: return null
            if (element is JsonNull) return null
            return KJson.lenientJson.decodeFromJsonElement(serializer(), element)
        }

        private fun cachedPlanet(spm: SharedPrefManager): MyPlanet? = fromVersionDetail(spm.getVersionDetail())

        fun getMyPlanetActivities(
            context: Context,
            spm: SharedPrefManager,
            model: UserEntity,
            now: Long = System.currentTimeMillis()
        ): JsonObject {
            val planet = cachedPlanet(spm)
            val usages = getTabletUsages(context, spm, now)
            return buildJsonObject {
                if (planet != null) put("planetVersion", planet.planetVersion)
                put("_id", VersionUtils.getAndroidId(context) + "@" + NetworkUtils.getUniqueIdentifier())
                put("last_synced", spm.getLastSync())
                put("parentCode", model.parentCode)
                put("createdOn", model.planetCode)
                put("type", "usages")
                put("usages", usages)
            }
        }

        fun getNormalMyPlanetActivities(context: Context, spm: SharedPrefManager, model: UserEntity): JsonObject {
            val planet = cachedPlanet(spm)
            return buildJsonObject {
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
                addDocumentOrigin()
            }
        }

        fun getTabletUsages(
            context: Context,
            spm: SharedPrefManager,
            now: Long = System.currentTimeMillis()
        ): JsonArray {
            val cal = Calendar.getInstance()
            cal.timeInMillis = spm.getLastUsageUploaded()
            val mUsageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val queryUsageStats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, now)
            return buildJsonArray {
                if (queryUsageStats != null) {
                    val packageName = context.packageName
                    val customDeviceName = NetworkUtils.getCustomDeviceName(context)
                    val deviceName = NetworkUtils.getDeviceName()
                    for (s in queryUsageStats) {
                        addStats(s, this, context, packageName, customDeviceName, deviceName, now)
                    }
                }
            }
        }

        private fun addStats(
            s: UsageStats,
            arr: JsonArrayBuilder,
            context: Context,
            packageName: String,
            customDeviceName: String,
            deviceName: String,
            now: Long
        ) {
            if (s.packageName == packageName) {
                val totalUsed = s.lastTimeUsed - s.firstTimeStamp
                arr.add(buildJsonObject {
                    put("lastTimeUsed", if (s.lastTimeUsed > 0) s.lastTimeUsed else 0)
                    put("firstTimeUsed", if (s.firstTimeStamp > 0) s.lastTimeStamp else 0)
                    put("totalForegroundTime", s.totalTimeInForeground)
                    put("totalUsed", if (totalUsed > 0) totalUsed else 0)
                    put("version", VersionUtils.getVersionCode(context))
                    put("versionName", VersionUtils.getVersionName(context))
                    put("customDeviceName", customDeviceName)
                    put("deviceName", deviceName)
                    put("time", now)
                    addDocumentOrigin()
                })
            }
        }
    }
}
