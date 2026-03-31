package org.ole.planet.myplanet.services.sync

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdaptiveBatchProcessorTest {

    private lateinit var context: Context
    private lateinit var activityManager: ActivityManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var adaptiveBatchProcessor: AdaptiveBatchProcessor

    @Before
    fun setUp() {
        context = mockk()
        activityManager = mockk()
        connectivityManager = mockk()

        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager

        // Mock getting MemoryInfo
        every { activityManager.getMemoryInfo(any()) } answers {
            val memInfo = firstArg<ActivityManager.MemoryInfo>()
            memInfo.availMem = 1024L * 1024L * 1024L // 1GB
        }
        every { activityManager.isLowRamDevice } returns false

        // Default to FAST network
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { capabilities.linkDownstreamBandwidthKbps } returns 100000
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false

        adaptiveBatchProcessor = AdaptiveBatchProcessor(context)

    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetOptimalConfigForResources_MediumNetwork_HighMemory() {
        val config = adaptiveBatchProcessor.getOptimalConfig("resources")

        // Android test framework defaults SDK_INT to 0. So Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q will be false
        // networkSpeed = MEDIUM
        // baseBatchSize = 500
        // memoryAdjustedBatchSize = min(500, 1024/10) = min(500, 102) = 102
        // max(50, 102) = 102
        assertEquals(102, config.batchSize)
        assertTrue(config.enableOptimizations)
        assertEquals(30000, config.timeoutMs)
    }

    @Test
    fun testGetOptimalConfigForCourses_MediumNetwork_HighMemory() {
        val config = adaptiveBatchProcessor.getOptimalConfig("courses")

        // courses config gets resources config, then halves batchSize
        // 102 / 2 = 51
        assertEquals(51, config.batchSize)
    }

    @Test
    fun testGetOptimalConfigForLibrary_MediumNetwork() {
        val config = adaptiveBatchProcessor.getOptimalConfig("library")

        // networkSpeed = MEDIUM
        // Library batch size for medium = 15
        assertEquals(15, config.batchSize)
        assertTrue(config.enableOptimizations)
    }

    @Test
    fun testGetOptimalConfigForStandard() {
        val config = adaptiveBatchProcessor.getOptimalConfig("other_table")

        assertEquals(50, config.batchSize)
        assertTrue(config.enableOptimizations)
    }
}
