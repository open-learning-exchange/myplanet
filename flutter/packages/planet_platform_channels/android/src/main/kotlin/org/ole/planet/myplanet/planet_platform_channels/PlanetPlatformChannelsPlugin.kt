package org.ole.planet.myplanet.planet_platform_channels

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodChannel
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * The `disk_stats` and `device_stats` method channels.
 *
 * These used to be registered in `MainActivity.configureFlutterEngine`, which
 * only runs for the Activity's engine — a headless engine started by the
 * `workmanager` plugin never passed through it, so every call from background
 * work threw `MissingPluginException` and the Dart side fell back to its
 * UI-primed preference caches (Phases 45–46). As a plugin package on a path
 * dependency, this class lands in `GeneratedPluginRegistrant` and is attached
 * to *every* engine, foreground and headless alike.
 *
 * Everything here needs only the application [Context]: system services, the
 * content resolver, and the package manager. Nothing touches an Activity,
 * which is exactly why the Activity was the wrong home for it.
 */
class PlanetPlatformChannelsPlugin : FlutterPlugin {
    private var diskChannel: MethodChannel? = null
    private var deviceChannel: MethodChannel? = null
    private lateinit var context: Context

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext

        diskChannel = MethodChannel(binding.binaryMessenger, "disk_stats").apply {
            setMethodCallHandler { call, result ->
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
        }

        deviceChannel = MethodChannel(binding.binaryMessenger, "device_stats").apply {
            setMethodCallHandler { call, result ->
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
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        diskChannel?.setMethodCallHandler(null)
        deviceChannel?.setMethodCallHandler(null)
        diskChannel = null
        deviceChannel = null
    }

    /** Port of `FileUtils.getStorageStats`: the primary storage volume's total
     * and free bytes, read through `StorageStatsManager`. `StorageVolume.uuid`
     * is null on emulators without emulated storage, so fall back to the
     * default UUID the way the Kotlin app does. */
    private fun storageStats(): Pair<Long, Long> {
        val storageStatsManager =
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val storageManager =
            context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolume = storageManager.primaryStorageVolume
        val uuid =
            storageVolume.uuid?.let { UUID.fromString(it) }
                ?: StorageManager.UUID_DEFAULT
        val totalBytes = storageStatsManager.getTotalBytes(uuid)
        val availableBytes = storageStatsManager.getFreeBytes(uuid)
        return Pair(totalBytes, availableBytes)
    }

    /** Port of `VersionUtils.getAndroidId` — `Settings.Secure.ANDROID_ID`. */
    private fun androidId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""

    /** Port of `NetworkUtils.getUniqueIdentifier` — `androidId + "_" + Build.ID`. */
    private fun uniqueIdentifier(): String = "${androidId()}_${Build.ID}"

    /** Port of `NetworkUtils.getDeviceName` — `MANUFACTURER MODEL`, uppercased,
     * collapsing the redundant case where the model already starts with the
     * manufacturer. */
    private fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer)) {
            model.uppercase(Locale.ROOT)
        } else {
            "$manufacturer $model".uppercase(Locale.ROOT)
        }
    }

    /** Port of `VersionUtils.getVersionCode`. */
    private fun versionCode(): Int =
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            // PackageInfoCompat without the androidx.core dependency: the
            // plugin's minSdk is 26, so only the P fork is needed.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        } catch (e: Exception) {
            0
        }

    /** Port of `VersionUtils.getVersionName`. */
    private fun versionName(): String? =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            null
        }

    /** Port of `MyPlanet.getTabletUsages` — a daily `UsageStatsManager` query
     * restricted to this app's own package, since [sinceMillis]. Returns an
     * empty list when the user has not granted `PACKAGE_USAGE_STATS` (the
     * system query simply yields nothing), matching the Kotlin app.
     *
     * Each row carries only what the query measures. `addStats`'s three
     * device-identity fields (`androidId`, `customDeviceName`, `deviceName`)
     * are filled by `MyPlanetActivitiesUploader` instead: `androidId` there is
     * the `getUniqueIdentifier()` composite rather than the bare ANDROID_ID,
     * and `customDeviceName` is a preference this layer cannot read. Reporting
     * them from here produced a plausible-looking wrong value. */
    private fun tabletUsages(sinceMillis: Long): List<Map<String, Any>> {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            sinceMillis,
            now,
        )
        val packageName = context.packageName
        val version = versionCode()
        val versionNameValue = versionName() ?: ""
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
                    "time" to Date().time,
                )
            }
    }
}
