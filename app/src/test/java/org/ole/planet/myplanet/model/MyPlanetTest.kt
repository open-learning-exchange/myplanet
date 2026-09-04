package org.ole.planet.myplanet.model

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.provider.Settings
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.NetworkUtils

class MyPlanetTest {
    private lateinit var context: Context
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var usageStatsManager: UsageStatsManager

    @Before
    fun setup() {
        mockkStatic(Settings.Secure::class)
        every { Settings.Secure.getString(any(), Settings.Secure.ANDROID_ID) } returns "mock_android_id"

        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "mock_unique_id"
        every { NetworkUtils.getDeviceName() } returns "mock_device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "mock_custom_device"

        context = mockk(relaxed = true)
        MainApplication.testContext = context
        sharedPrefManager = mockk(relaxed = true)
        usageStatsManager = mockk(relaxed = true)

        every { context.getSystemService(Context.USAGE_STATS_SERVICE) } returns usageStatsManager
        every { context.packageName } returns "org.ole.planet.myplanet"
    }

    @After
    fun tearDown() {
        unmockkObject(NetworkUtils)
        unmockkStatic(Settings.Secure::class)
    }

    @Test
    fun `getTabletUsages queries UsageStatsManager with pinned now timestamp`() {
        val lastUsageUploaded = 1000L
        val pinnedNow = 5000L
        every { sharedPrefManager.getLastUsageUploaded() } returns lastUsageUploaded

        val mockUsageStats = mockk<UsageStats>(relaxed = true) {
            every { packageName } returns "org.ole.planet.myplanet"
            every { lastTimeUsed } returns 4000L
            every { firstTimeStamp } returns 2000L
            every { lastTimeStamp } returns 3000L
            every { totalTimeInForeground } returns 1000L
        }

        every {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                lastUsageUploaded,
                pinnedNow
            )
        } returns listOf(mockUsageStats)

        val result = MyPlanet.getTabletUsages(context, sharedPrefManager, now = pinnedNow)

        verify(exactly = 1) {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                lastUsageUploaded,
                pinnedNow
            )
        }

        assertEquals(1, result.size())
        val statJson = result[0].asJsonObject
        assertEquals(4000L, statJson.get("lastTimeUsed").asLong)
        assertEquals(3000L, statJson.get("firstTimeUsed").asLong)
        assertEquals(1000L, statJson.get("totalForegroundTime").asLong)
        assertEquals(2000L, statJson.get("totalUsed").asLong)
    }

    @Test
    fun `getMyPlanetActivities uses pinned now for tablet usages`() {
        val lastUsageUploaded = 1000L
        val pinnedNow = 5000L
        val userModel = UserEntity().apply {
            parentCode = "parent123"
            planetCode = "planet123"
        }
        every { sharedPrefManager.getLastUsageUploaded() } returns lastUsageUploaded
        every {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                lastUsageUploaded,
                pinnedNow
            )
        } returns emptyList()

        val json = MyPlanet.getMyPlanetActivities(context, sharedPrefManager, userModel, now = pinnedNow)

        verify(exactly = 1) {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                lastUsageUploaded,
                pinnedNow
            )
        }

        assertEquals("usages", json.get("type").asString)
        assertEquals("parent123", json.get("parentCode").asString)
        assertEquals("planet123", json.get("createdOn").asString)
        assertEquals(0, json.getAsJsonArray("usages").size())
    }
}
