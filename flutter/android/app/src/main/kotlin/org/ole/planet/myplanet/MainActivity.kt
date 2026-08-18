package org.ole.planet.myplanet

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.UUID

class MainActivity : FlutterActivity() {
    private val channel = "disk_stats"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channel)
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
}
