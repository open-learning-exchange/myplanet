package org.ole.planet.myplanet.services.sync

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlin.math.max
import kotlin.math.min

data class SystemCapabilities(
    val availableMemoryMB: Long,
    val cpuCores: Int,
    val networkSpeed: NetworkSpeed,
    val isLowMemoryDevice: Boolean
)

enum class NetworkSpeed {
    SLOW, MEDIUM, FAST, UNKNOWN
}

class AdaptiveBatchProcessor(private val context: Context) {
    
    private val baseConfig = SyncConfig()
    private var cachedCapabilities: SystemCapabilities? = null
    private var lastCapabilityCheck = 0L
    private val cacheValidityMs = 30000L // 30 seconds
    
    fun getOptimalConfig(table: String): SyncConfig {
        val capabilities = getSystemCapabilities()
        return when (table) {
            "resources" -> getResourceSyncConfig(capabilities)
            "courses", "exams", "submissions" -> getCourseSyncConfig(capabilities)
            "library", "shelf" -> getLibrarySyncConfig(capabilities)
            else -> getStandardSyncConfig(capabilities)
        }
    }
    
    private fun getSystemCapabilities(): SystemCapabilities {
        val now = System.currentTimeMillis()
        if (cachedCapabilities != null && (now - lastCapabilityCheck) < cacheValidityMs) {
            return cachedCapabilities!!
        }
        
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val capabilities = SystemCapabilities(
            availableMemoryMB = memInfo.availMem / (1024 * 1024),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            networkSpeed = detectNetworkSpeed(),
            isLowMemoryDevice = activityManager.isLowRamDevice
        )
        
        cachedCapabilities = capabilities
        lastCapabilityCheck = now
        return capabilities
    }
    
    private fun detectNetworkSpeed(): NetworkSpeed {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return NetworkSpeed.UNKNOWN
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkSpeed.UNKNOWN
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> detectWifiSpeed(capabilities)
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> detectCellularSpeed(capabilities)
            else -> NetworkSpeed.SLOW
        }
    }
    
    private fun detectWifiSpeed(capabilities: NetworkCapabilities): NetworkSpeed {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val linkDownstreamBandwidth = capabilities.linkDownstreamBandwidthKbps
            when {
                linkDownstreamBandwidth > 50000 -> NetworkSpeed.FAST
                linkDownstreamBandwidth > 10000 -> NetworkSpeed.MEDIUM
                else -> NetworkSpeed.SLOW
            }
        } else {
            NetworkSpeed.MEDIUM
        }
    }

    private fun detectCellularSpeed(capabilities: NetworkCapabilities): NetworkSpeed {
        return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            NetworkSpeed.MEDIUM
        } else {
            NetworkSpeed.SLOW
        }
    }

    private fun getResourceSyncConfig(capabilities: SystemCapabilities): SyncConfig {
        return baseConfig.copy(
            batchSize = calculateResourceBatchSize(capabilities),
            concurrencyLevel = calculateOptimalConcurrency(capabilities),
            enableOptimizations = !capabilities.isLowMemoryDevice,
            timeoutMs = calculateTimeoutMs(capabilities.networkSpeed)
        )
    }

    private fun calculateResourceBatchSize(capabilities: SystemCapabilities): Int {
        val baseBatchSize = when (capabilities.networkSpeed) {
            NetworkSpeed.FAST -> 1000
            NetworkSpeed.MEDIUM -> 500
            NetworkSpeed.SLOW -> 100
            NetworkSpeed.UNKNOWN -> 250
        }
        
        val memoryAdjustedBatchSize = if (capabilities.isLowMemoryDevice) {
            baseBatchSize / 2
        } else {
            min(baseBatchSize, (capabilities.availableMemoryMB / 10).toInt())
        }
        
        return max(50, memoryAdjustedBatchSize)
    }

    private fun calculateTimeoutMs(networkSpeed: NetworkSpeed): Long {
        return when (networkSpeed) {
            NetworkSpeed.FAST -> 15000L
            NetworkSpeed.MEDIUM -> 30000L
            NetworkSpeed.SLOW -> 60000L
            NetworkSpeed.UNKNOWN -> 45000L
        }
    }
    
    private fun getCourseSyncConfig(capabilities: SystemCapabilities): SyncConfig {
        val resourceConfig = getResourceSyncConfig(capabilities)
        return resourceConfig.copy(
            batchSize = resourceConfig.batchSize / 2,
            concurrencyLevel = max(1, resourceConfig.concurrencyLevel - 1)
        )
    }
    
    private fun getLibrarySyncConfig(capabilities: SystemCapabilities): SyncConfig {
        return baseConfig.copy(
            batchSize = when (capabilities.networkSpeed) {
                NetworkSpeed.FAST -> 25
                NetworkSpeed.MEDIUM -> 15
                NetworkSpeed.SLOW -> 5
                NetworkSpeed.UNKNOWN -> 10
            },
            concurrencyLevel = calculateOptimalConcurrency(capabilities),
            enableOptimizations = true
        )
    }
    
    private fun getStandardSyncConfig(capabilities: SystemCapabilities): SyncConfig {
        return baseConfig.copy(
            batchSize = 50,
            concurrencyLevel = max(1, capabilities.cpuCores / 2),
            enableOptimizations = !capabilities.isLowMemoryDevice
        )
    }
    
    private fun calculateOptimalConcurrency(capabilities: SystemCapabilities): Int {
        val baseConcurrency = when {
            capabilities.isLowMemoryDevice -> 1
            capabilities.availableMemoryMB < 512 -> 2
            capabilities.availableMemoryMB < 1024 -> 3
            else -> min(5, capabilities.cpuCores)
        }
        
        return when (capabilities.networkSpeed) {
            NetworkSpeed.FAST -> baseConcurrency
            NetworkSpeed.MEDIUM -> max(1, baseConcurrency - 1)
            NetworkSpeed.SLOW -> max(1, baseConcurrency - 2)
            NetworkSpeed.UNKNOWN -> max(1, baseConcurrency - 1)
        }
    }
    
}
