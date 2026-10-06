package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.AppInfo
import org.ole.planet.myplanet.utils.AppUsageStat
import org.ole.planet.myplanet.utils.AppUsageStats
import org.ole.planet.myplanet.utils.DateTimeUtils
import org.ole.planet.myplanet.utils.DeviceNameProvider
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

// App-side activity reports built around MyPlanet, kept out of the shared version DTO.

fun MyPlanet.Companion.getMyPlanetActivities(
    appInfo: AppInfo,
    deviceNameProvider: DeviceNameProvider,
    usageStats: AppUsageStats,
    spm: SharedPrefManager,
    model: UserEntity,
    now: Long = DateTimeUtils.nowMillis()
): JsonObject {
    val planet = GsonUtils.gson.fromJson(spm.getVersionDetail() ?: "", MyPlanet::class.java)
    val usages = getTabletUsages(appInfo, deviceNameProvider, usageStats, spm, now)
    return buildJsonObject {
        if (planet != null) put("planetVersion", planet.planetVersion)
        put("_id", appInfo.androidId() + "@" + NetworkUtils.getUniqueIdentifier())
        put("last_synced", spm.getLastSync())
        put("parentCode", model.parentCode)
        put("createdOn", model.planetCode)
        put("type", "usages")
        put("usages", usages.toKotlinx())
    }.toGson()
}

fun MyPlanet.Companion.getNormalMyPlanetActivities(
    appInfo: AppInfo,
    deviceNameProvider: DeviceNameProvider,
    spm: SharedPrefManager,
    model: UserEntity
): JsonObject {
    val planet = GsonUtils.gson.fromJson(spm.getVersionDetail() ?: "", MyPlanet::class.java)
    val postJSON = buildJsonObject {
        if (planet != null) put("planetVersion", planet.planetVersion)
        put("last_synced", spm.getLastSync())
        put("parentCode", model.parentCode)
        put("createdOn", model.planetCode)
        put("version", appInfo.versionCode())
        put("versionName", appInfo.versionName())
        put("uniqueAndroidId", appInfo.androidId())
        put("customDeviceName", deviceNameProvider.getCustomDeviceName())
        put("deviceName", deviceNameProvider.getDeviceName())
        put("time", DateTimeUtils.nowMillis())
        put("type", "sync")
    }.toGson()
    postJSON.addDocumentOrigin()
    return postJSON
}

fun MyPlanet.Companion.getTabletUsages(
    appInfo: AppInfo,
    deviceNameProvider: DeviceNameProvider,
    usageStats: AppUsageStats,
    spm: SharedPrefManager,
    now: Long = DateTimeUtils.nowMillis()
): JsonArray {
    val arr = JsonArray()
    val queryUsageStats = usageStats.queryDailyUsage(spm.getLastUsageUploaded(), now)
    if (queryUsageStats != null) {
        val packageName = appInfo.packageName
        val customDeviceName = deviceNameProvider.getCustomDeviceName()
        val deviceName = deviceNameProvider.getDeviceName()
        for (s in queryUsageStats) {
            addStats(s, arr, appInfo, packageName, customDeviceName, deviceName, now)
        }
    }
    return arr
}

private fun addStats(
    s: AppUsageStat,
    arr: JsonArray,
    appInfo: AppInfo,
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
            put("version", appInfo.versionCode())
            put("versionName", appInfo.versionName())
            put("customDeviceName", customDeviceName)
            put("deviceName", deviceName)
            put("time", now)
        }.toGson()
        `object`.addDocumentOrigin()
        arr.add(`object`)
    }
}
