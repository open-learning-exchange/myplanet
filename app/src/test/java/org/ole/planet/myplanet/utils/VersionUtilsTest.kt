package org.ole.planet.myplanet.utils

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VersionUtilsTest {

    @Before
    fun setUp() {
        VersionUtils.resetAndroidIdCacheForTesting()
    }

    @Test
    fun getVersionCode_should_return_0_on_NameNotFoundException() {
        val mockContext = mockk<Context>()
        val mockPackageManager = mockk<PackageManager>()
        val exception = PackageManager.NameNotFoundException()

        every { mockContext.packageName } returns "org.ole.planet.myplanet"
        every { mockContext.packageManager } returns mockPackageManager
        every { mockPackageManager.getPackageInfo("org.ole.planet.myplanet", 0) } throws exception

        val versionCode = VersionUtils.getVersionCode(mockContext)
        assertEquals(0, versionCode)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O])
    fun getVersionCode_should_return_versionCode_for_pre_P() {
        val mockContext = mockk<Context>()
        val mockPackageManager = mockk<PackageManager>()
        val mockPackageInfo = PackageInfo()

        @Suppress("DEPRECATION")
        mockPackageInfo.versionCode = 123

        every { mockContext.packageName } returns "org.ole.planet.myplanet"
        every { mockContext.packageManager } returns mockPackageManager
        every { mockPackageManager.getPackageInfo("org.ole.planet.myplanet", 0) } returns mockPackageInfo

        val versionCode = VersionUtils.getVersionCode(mockContext)
        assertEquals(123, versionCode)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun getVersionCode_should_return_longVersionCode_for_P_and_above() {
        val mockContext = mockk<Context>()
        val mockPackageManager = mockk<PackageManager>()
        val mockPackageInfo = PackageInfo()

        mockPackageInfo.longVersionCode = 456L

        every { mockContext.packageName } returns "org.ole.planet.myplanet"
        every { mockContext.packageManager } returns mockPackageManager
        every { mockPackageManager.getPackageInfo("org.ole.planet.myplanet", 0) } returns mockPackageInfo

        val versionCode = VersionUtils.getVersionCode(mockContext)
        assertEquals(456, versionCode)
    }

    @Test
    fun getVersionName_should_return_empty_string_on_NameNotFoundException() {
        val mockContext = mockk<Context>()
        val mockPackageManager = mockk<PackageManager>()
        val exception = PackageManager.NameNotFoundException()

        every { mockContext.packageName } returns "org.ole.planet.myplanet"
        every { mockContext.packageManager } returns mockPackageManager
        every { mockPackageManager.getPackageInfo("org.ole.planet.myplanet", 0) } throws exception

        val versionName = VersionUtils.getVersionName(mockContext)
        assertEquals("", versionName)
    }

    @Test
    fun getVersionName_should_return_versionName() {
        val mockContext = mockk<Context>()
        val mockPackageManager = mockk<PackageManager>()
        val mockPackageInfo = PackageInfo()

        mockPackageInfo.versionName = "1.2.3"

        every { mockContext.packageName } returns "org.ole.planet.myplanet"
        every { mockContext.packageManager } returns mockPackageManager
        every { mockPackageManager.getPackageInfo("org.ole.planet.myplanet", 0) } returns mockPackageInfo

        val versionName = VersionUtils.getVersionName(mockContext)
        assertEquals("1.2.3", versionName)
    }

    @Test
    fun getVersionName_should_return_null_when_versionName_is_null() {
        val mockContext = mockk<Context>()
        val mockPackageManager = mockk<PackageManager>()
        val mockPackageInfo = PackageInfo()

        mockPackageInfo.versionName = null

        every { mockContext.packageName } returns "org.ole.planet.myplanet"
        every { mockContext.packageManager } returns mockPackageManager
        every { mockPackageManager.getPackageInfo("org.ole.planet.myplanet", 0) } returns mockPackageInfo

        val versionName = VersionUtils.getVersionName(mockContext)
        assertNull(versionName)
    }

    @Test
    fun getAndroidId_caches_non_null_id() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
            "initial_android_id"
        )

        val firstCallId = VersionUtils.getAndroidId(context)
        assertEquals("initial_android_id", firstCallId)

        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
            "changed_android_id"
        )

        val secondCallId = VersionUtils.getAndroidId(context)
        assertEquals("initial_android_id", secondCallId)
    }

    @Test
    fun getAndroidId_does_not_cache_null_value() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
            null
        )

        val firstCallId = VersionUtils.getAndroidId(context)
        assertNull(firstCallId)

        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
            "valid_android_id"
        )

        val secondCallId = VersionUtils.getAndroidId(context)
        assertEquals("valid_android_id", secondCallId)
    }
}
