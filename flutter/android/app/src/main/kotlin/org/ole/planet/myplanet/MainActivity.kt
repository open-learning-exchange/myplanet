package org.ole.planet.myplanet

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageStatsManager
import android.os.storage.StorageVolume
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat.getLongVersionCode
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "disk_stats")
            .setMethodCallHandler { call, result ->
                if (call.method != "getStorageStats") {
                    result.notImplemented()
                    return@setMethodCallHandler
                }
                try {
                    val stats = storageStats()
                    result.success(
                        mapOf(
                            "totalBytes" to stats.first,
                            "availableBytes" to stats.second,
                        ),
                    )
                } catch (e: Exception) {
                    result.error("disk_stats_unavailable", e.message, null)
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "device_stats")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getAndroidId" -> result.success(androidId())
                    "getUniqueIdentifier" -> result.success(uniqueIdentifier())
                    "getDeviceName" -> result.success(deviceName())
                    "getVersionCode" -> result.success(versionCode())
                    "getVersionName" -> result.success(versionName())
                    "getTabletUsages" -> {
                        val since = (call.argument<Number>("sinceMillis") ?: 0).toLong()
                        result.success(tabletUsages(since))
                    }
                    else -> result.notImplemented()
                }
            }
    }

    /// Port of `FileUtils.getStorageStats`: the primary storage volume's total
    /// and free bytes, read through `StorageStatsManager`. `StorageVolume.uuid`
    /// is null on emulators without emulated storage, so fall back to the
    /// default UUID the way the Kotlin does.
    private fun storageStats(): Pair<Long, Long> {
        val storageStatsManager =
            getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolume = storageManager.primaryStorageVolume
        val uuid =
            storageVolume.uuid?.let { UUID.fromString(it) }
                ?: StorageManager.UUID_DEFAULT
        val totalBytes = storageStatsManager.getTotalBytes(uuid)
        val availableBytes = storageStatsManager.getFreeBytes(uuid)
        return Pair(totalBytes, availableBytes)
    }

    /// Port of `VersionUtils.getAndroidId` -- `Settings.Secure.ANDROID_ID`.
    private fun androidId(): String =
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""

    /// Port of `NetworkUtils.getUniqueIdentifier` -- `androidId + "_" + Build.ID`.
    private fun uniqueIdentifier(): String = "${androidId()}_${Build.ID}"

    /// Port of `NetworkUtils.getDeviceName` -- `MANUFACTURER MODEL`, uppercased,
    /// collapsing the redundant case where the model already starts with the
    /// manufacturer.
    private fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer)) {
            model.uppercase(Locale.ROOT)
        } else {
            "$manufacturer $model".uppercase(Locale.ROOT)
        }
    }

    /// Port of `VersionUtils.getVersionCode`.
    private fun versionCode(): Int =
        try {
            val info = packageManager.getPackageInfo(packageName, 0)
            getLongVersionCode(info).toInt()
        } catch (e: Exception) {
            0
        }

    /// Port of `VersionUtils.getVersionName`.
    private fun versionName(): String? =
        try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }

    /// Port of `MyPlanet.getTabletUsages` -- a daily `UsageStatsManager` query
    /// restricted to this app's own package, since [sinceMillis]. Each stat is
    /// serialized to the field set `MyPlanet.addStats` writes. Returns an empty
    /// list when the user has not granted `PACKAGE_USAGE_STATS` (the system
    /// query simply yields nothing), matching the Kotlin.
    private fun tabletUsages(sinceMillis: Long): List<Map<String, Any>> {
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            sinceMillis,
            now,
        )
        val packageName = packageName
        val version = versionCode()
        val versionNameValue = versionName() ?: ""
        val androidIdValue = androidId()
        val deviceNameValue = deviceName()
        return stats
            .orEmpty()
            .filter { it.packageName == packageName }
            .map { s ->
                val totalUsed = s.lastTimeUsed - s.firstTimeStamp
                mapOf(
                    "lastTimeUsed" to (if (s.lastTimeUsed > 0) s.lastTimeUsed else 0),
                    "firstTimeUsed" to (if (s.firstTimeStamp > 0) s.lastTimeStamp else 0),
                    "totalForegroundTime" to s.totalTimeInForeground,
                    "totalUsed" to (if (totalUsed > 0) totalUsed else 0),
                    "version" to version,
                    "versionName" to versionNameValue,
                    "androidId" to androidIdValue,
                    "customDeviceName" to "",
                    "deviceName" to deviceNameValue,
                    "time" to Date().time,
                )
            }
    }
}
