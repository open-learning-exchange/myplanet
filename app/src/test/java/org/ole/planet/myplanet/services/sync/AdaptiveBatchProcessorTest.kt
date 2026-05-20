package org.ole.planet.myplanet.services.sync

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q], application = android.app.Application::class)
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

        setupCapabilities(1024L * 1024L * 1024L, false, isWifi = true, isCellular = false, bandwidth = 100000)

        adaptiveBatchProcessor = AdaptiveBatchProcessor(context)
    }

    private fun setupCapabilities(availMemBytes: Long, isLowRam: Boolean, isWifi: Boolean, isCellular: Boolean, bandwidth: Int, hasInternet: Boolean = true) {
        every { activityManager.getMemoryInfo(any()) } answers {
            val memInfo = firstArg<ActivityManager.MemoryInfo>()
            memInfo.availMem = availMemBytes
        }
        every { activityManager.isLowRamDevice } returns isLowRam

        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns isWifi
        every { capabilities.linkDownstreamBandwidthKbps } returns bandwidth
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns isCellular
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns hasInternet
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetOptimalConfigForResources_FastNetwork_HighMemory() {
        val config = adaptiveBatchProcessor.getOptimalConfig("resources")

        // Build.VERSION.SDK_INT = Q (from Robolectric)
        // networkSpeed = FAST
        // baseBatchSize = 1000
        // memoryAdjustedBatchSize = min(1000, 1024/10) = min(1000, 102) = 102
        // max(50, 102) = 102
        assertEquals(102, config.batchSize)
        assertTrue(config.enableOptimizations)
        assertEquals(15000, config.timeoutMs)
    }

    @Test
    fun testGetOptimalConfigForCourses_FastNetwork_HighMemory() {
        val config = adaptiveBatchProcessor.getOptimalConfig("courses")

        // courses config gets resources config, then halves batchSize
        // 102 / 2 = 51
        assertEquals(51, config.batchSize)
    }

    @Test
    fun testGetOptimalConfigForLibrary_FastNetwork() {
        val config = adaptiveBatchProcessor.getOptimalConfig("library")

        // networkSpeed = FAST
        // Library batch size for fast = 25
        assertEquals(25, config.batchSize)
        assertTrue(config.enableOptimizations)
    }

    @Test
    fun testGetOptimalConfigForStandard() {
        val config = adaptiveBatchProcessor.getOptimalConfig("other_table")

        assertEquals(50, config.batchSize)
        assertTrue(config.enableOptimizations)
    }

    @Test
    fun testGetOptimalConfigForResources_SlowNetwork_LowMemory() {
        setupCapabilities(256L * 1024L * 1024L, true, isWifi = false, isCellular = true, bandwidth = 0, hasInternet = false)

        // Forces capability refresh by creating new AdaptiveBatchProcessor to clear cache
        adaptiveBatchProcessor = AdaptiveBatchProcessor(context)

        val config = adaptiveBatchProcessor.getOptimalConfig("resources")

        // networkSpeed = SLOW
        // baseBatchSize = 100
        // memoryAdjustedBatchSize = 100 / 2 = 50
        // max(50, 50) = 50
        assertEquals(50, config.batchSize)
        assertFalse(config.enableOptimizations)
        assertEquals(60000, config.timeoutMs)
        assertEquals(1, config.concurrencyLevel)
    }

    @Test
    fun testGetOptimalConfigForLibrary_MediumNetwork_Cellular() {
        setupCapabilities(2048L * 1024L * 1024L, false, isWifi = false, isCellular = true, bandwidth = 0, hasInternet = true)

        adaptiveBatchProcessor = AdaptiveBatchProcessor(context)

        val config = adaptiveBatchProcessor.getOptimalConfig("library")

        // networkSpeed = MEDIUM
        // Library batch size for medium = 15
        assertEquals(15, config.batchSize)
        assertTrue(config.enableOptimizations)
    }

    @Test
    fun testGetOptimalConfigForCourses_UnknownNetwork_MidMemory() {
        // network without known transports returns NetworkSpeed.SLOW, not UNKNOWN
        // activeNetwork == null returns UNKNOWN, so we'll test both here
        every { connectivityManager.activeNetwork } returns null

        adaptiveBatchProcessor = AdaptiveBatchProcessor(context)

        val config = adaptiveBatchProcessor.getOptimalConfig("courses")

        // networkSpeed = UNKNOWN
        // resource baseBatchSize = 250
        // memoryAdjustedBatchSize = min(250, 1024/10=102) = 102
        // courses batchSize = 102 / 2 = 51
        assertEquals(51, config.batchSize)
        assertTrue(config.enableOptimizations)
        assertEquals(45000, config.timeoutMs)

        // Create new capability instance to test the else branch for SLOW
        val context2 = mockk<Context>()
        val activityManager2 = mockk<ActivityManager>()
        val connectivityManager2 = mockk<ConnectivityManager>()
        every { context2.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager2
        every { context2.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager2
        every { activityManager2.getMemoryInfo(any()) } answers {
            val memInfo = firstArg<ActivityManager.MemoryInfo>()
            memInfo.availMem = 800L * 1024L * 1024L
        }
        every { activityManager2.isLowRamDevice } returns false

        val network2 = mockk<Network>()
        val capabilities2 = mockk<NetworkCapabilities>()
        every { connectivityManager2.activeNetwork } returns network2
        every { connectivityManager2.getNetworkCapabilities(network2) } returns capabilities2
        every { capabilities2.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { capabilities2.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false

        val processor2 = AdaptiveBatchProcessor(context2)
        val config2 = processor2.getOptimalConfig("courses")

        // networkSpeed = SLOW
        // resource baseBatchSize = 100
        // memoryAdjustedBatchSize = min(100, 800/10=80) = 80
        // courses batchSize = 80 / 2 = 40
        assertEquals(40, config2.batchSize)
        assertTrue(config2.enableOptimizations)
        assertEquals(60000, config2.timeoutMs)
    }

    @Test
    fun testGetOptimalConfigForSubmissions_And_Exams() {
        val configExams = adaptiveBatchProcessor.getOptimalConfig("exams")
        val configSubmissions = adaptiveBatchProcessor.getOptimalConfig("submissions")

        // Should use course configuration
        assertEquals(51, configExams.batchSize)
        assertEquals(51, configSubmissions.batchSize)
    }

    @Test
    fun testGetOptimalConfigForShelf() {
        val configShelf = adaptiveBatchProcessor.getOptimalConfig("shelf")

        // Should use library configuration
        assertEquals(25, configShelf.batchSize)
        assertTrue(configShelf.enableOptimizations)
    }

    @Test
    fun testCacheValidity() {
        // Initial setup for FAST network
        val config1 = adaptiveBatchProcessor.getOptimalConfig("resources")
        assertEquals(102, config1.batchSize)

        // Change setup to SLOW network but it shouldn't apply yet due to cache
        setupCapabilities(1024L * 1024L * 1024L, false, isWifi = true, isCellular = false, bandwidth = 1000)

        val config2 = adaptiveBatchProcessor.getOptimalConfig("resources")
        assertEquals(102, config2.batchSize) // Still FAST cached value

        // Simulate 31 seconds passing (not easily possible with System.currentTimeMillis() mocking in Kotlin/MockK easily without refactoring)
        // Instead we test that the same AdaptiveBatchProcessor instance reuses the result by verifying the method calls
        // Since getOptimalConfig doesn't expose a method, we can check network object interaction

        // Only one interaction with activeNetwork should have occurred because of caching
        io.mockk.verify(exactly = 1) { connectivityManager.activeNetwork }
    }
}
