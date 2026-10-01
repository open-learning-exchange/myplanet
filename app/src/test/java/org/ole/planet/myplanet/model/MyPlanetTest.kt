package org.ole.planet.myplanet.model

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.AppInfo
import org.ole.planet.myplanet.utils.AppUsageStat
import org.ole.planet.myplanet.utils.AppUsageStats
import org.ole.planet.myplanet.utils.DeviceNameProvider
import org.ole.planet.myplanet.utils.NetworkUtils

class MyPlanetTest {
    private lateinit var appInfo: AppInfo
    private lateinit var deviceNameProvider: DeviceNameProvider
    private lateinit var usageStats: AppUsageStats
    private lateinit var sharedPrefManager: SharedPrefManager

    @Before
    fun setup() {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "mock_unique_id"

        appInfo = mockk(relaxed = true)
        every { appInfo.packageName } returns "org.ole.planet.myplanet"
        every { appInfo.androidId() } returns "mock_android_id"
        every { appInfo.versionCode() } returns 7064
        every { appInfo.versionName() } returns "0.70.64"
        deviceNameProvider = mockk()
        every { deviceNameProvider.getDeviceName() } returns "mock_device"
        every { deviceNameProvider.getCustomDeviceName() } returns "mock_custom_device"
        usageStats = mockk()
        sharedPrefManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkObject(NetworkUtils)
    }

    private fun stat(
        packageName: String = "org.ole.planet.myplanet",
        lastTimeUsed: Long = 0L,
        firstTimeStamp: Long = 0L,
        lastTimeStamp: Long = 0L,
        totalTimeInForeground: Long = 0L
    ) = AppUsageStat(packageName, firstTimeStamp, lastTimeStamp, lastTimeUsed, totalTimeInForeground)

    @Test
    fun `getTabletUsages queries usage stats with pinned now timestamp`() {
        val lastUsageUploaded = 1000L
        val pinnedNow = 5000L
        every { sharedPrefManager.getLastUsageUploaded() } returns lastUsageUploaded
        every { usageStats.queryDailyUsage(lastUsageUploaded, pinnedNow) } returns listOf(
            stat(lastTimeUsed = 4000L, firstTimeStamp = 2000L, lastTimeStamp = 3000L, totalTimeInForeground = 1000L)
        )

        val result = MyPlanet.getTabletUsages(appInfo, deviceNameProvider, usageStats, sharedPrefManager, now = pinnedNow)

        verify(exactly = 1) { usageStats.queryDailyUsage(lastUsageUploaded, pinnedNow) }

        assertEquals(1, result.size())
        val statJson = result[0].asJsonObject
        assertEquals(4000L, statJson.get("lastTimeUsed").asLong)
        assertEquals(3000L, statJson.get("firstTimeUsed").asLong)
        assertEquals(1000L, statJson.get("totalForegroundTime").asLong)
        assertEquals(2000L, statJson.get("totalUsed").asLong)
        assertEquals(7064, statJson.get("version").asInt)
        assertEquals("0.70.64", statJson.get("versionName").asString)
        assertEquals("mock_custom_device", statJson.get("customDeviceName").asString)
        assertEquals("mock_device", statJson.get("deviceName").asString)
        assertEquals(pinnedNow, statJson.get("time").asLong)
    }

    @Test
    fun `getTabletUsages carries identical time across matching rows and skips non-matching packages`() {
        val lastUsageUploaded = 1000L
        val pinnedNow = 9999L
        every { sharedPrefManager.getLastUsageUploaded() } returns lastUsageUploaded
        every { usageStats.queryDailyUsage(lastUsageUploaded, pinnedNow) } returns listOf(
            stat(lastTimeUsed = 4000L, firstTimeStamp = 2000L, lastTimeStamp = 3000L, totalTimeInForeground = 1000L),
            stat(packageName = "com.other.app"),
            stat(lastTimeUsed = 8000L, firstTimeStamp = 5000L, lastTimeStamp = 6000L, totalTimeInForeground = 3000L)
        )

        val result = MyPlanet.getTabletUsages(appInfo, deviceNameProvider, usageStats, sharedPrefManager, now = pinnedNow)

        assertEquals(2, result.size())
        for (elem in result) {
            val statJson = elem.asJsonObject
            assertEquals(pinnedNow, statJson.get("time").asLong)
            assertEquals("mock_custom_device", statJson.get("customDeviceName").asString)
            assertEquals("mock_device", statJson.get("deviceName").asString)
        }
    }

    @Test
    fun `getTabletUsages returns empty array when platform returns no stats`() {
        every { sharedPrefManager.getLastUsageUploaded() } returns 0L
        every { usageStats.queryDailyUsage(any(), any()) } returns null

        val result = MyPlanet.getTabletUsages(appInfo, deviceNameProvider, usageStats, sharedPrefManager, now = 1L)

        assertEquals(0, result.size())
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
        every { usageStats.queryDailyUsage(lastUsageUploaded, pinnedNow) } returns emptyList()

        val json = MyPlanet.getMyPlanetActivities(
            appInfo, deviceNameProvider, usageStats, sharedPrefManager, userModel, now = pinnedNow
        )

        verify(exactly = 1) { usageStats.queryDailyUsage(lastUsageUploaded, pinnedNow) }

        assertEquals("usages", json.get("type").asString)
        assertEquals("mock_android_id@mock_unique_id", json.get("_id").asString)
        assertEquals("parent123", json.get("parentCode").asString)
        assertEquals("planet123", json.get("createdOn").asString)
        assertEquals(0, json.getAsJsonArray("usages").size())
    }

    @Test
    fun `getNormalMyPlanetActivities builds a sync-type payload with device metadata`() {
        val userModel = UserEntity().apply {
            parentCode = "parent123"
            planetCode = "planet123"
        }
        every { sharedPrefManager.getLastSync() } returns 123456789L
        every { sharedPrefManager.getVersionDetail() } returns null

        val json = MyPlanet.getNormalMyPlanetActivities(appInfo, deviceNameProvider, sharedPrefManager, userModel)

        assertEquals("sync", json.get("type").asString)
        assertEquals(123456789L, json.get("last_synced").asLong)
        assertEquals("parent123", json.get("parentCode").asString)
        assertEquals("planet123", json.get("createdOn").asString)
        assertEquals(7064, json.get("version").asInt)
        assertEquals("0.70.64", json.get("versionName").asString)
        assertEquals("mock_custom_device", json.get("customDeviceName").asString)
        assertEquals("mock_device", json.get("deviceName").asString)
        assertEquals("mock_android_id", json.get("uniqueAndroidId").asString)
        assertEquals(false, json.has("planetVersion"))
    }
}
