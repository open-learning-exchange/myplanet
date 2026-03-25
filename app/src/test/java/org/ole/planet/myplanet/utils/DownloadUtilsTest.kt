package org.ole.planet.myplanet.utils

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.services.DownloadService
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [Build.VERSION_CODES.S])
class DownloadUtilsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = spyk(ApplicationProvider.getApplicationContext())

        mockkStatic(Utilities::class)
        every { Utilities.toast(any(), any()) } returns Unit

        mockkObject(DownloadService.Companion)

        mockkStatic(WorkManager::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun invokeStartDownloadServiceSafely(urlsKey: String, fromSync: Boolean) {
        val method = DownloadUtils::class.java.getDeclaredMethod(
            "startDownloadServiceSafely",
            Context::class.java,
            String::class.java,
            Boolean::class.java
        )
        method.isAccessible = true
        method.invoke(DownloadUtils, context, urlsKey, fromSync)
    }

    @Test
    fun testStartDownloadServiceSafely_whenCanStartForegroundService_startsService() {
        val urlsKey = "test_key"
        val fromSync = false

        mockkObject(DownloadUtils)
        every { DownloadUtils.canStartForegroundService(any()) } returns true
        every { DownloadService.startService(any(), any(), any()) } returns Unit

        invokeStartDownloadServiceSafely(urlsKey, fromSync)

        verify(exactly = 1) { DownloadService.startService(context, urlsKey, fromSync) }
    }

    @Test
    fun testStartDownloadServiceSafely_whenException_showsToastAndStartsWork() {
        val urlsKey = "test_key"
        val fromSync = false

        mockkObject(DownloadUtils, recordPrivateCalls = true)
        every { DownloadUtils.canStartForegroundService(any()) } returns true
        every { DownloadService.startService(any(), any(), any()) } throws RuntimeException("Service error")
        every { DownloadUtils["startDownloadWork"](any<Context>(), any<String>(), any<Boolean>()) } returns Unit

        invokeStartDownloadServiceSafely(urlsKey, fromSync)

        verify(exactly = 1) { DownloadService.startService(context, urlsKey, fromSync) }
        verify(exactly = 1) { Utilities.toast(any(), any()) }
        verify(exactly = 1) { DownloadUtils["startDownloadWork"](context, urlsKey, fromSync) }
    }

    @Test
    fun testStartDownloadServiceSafely_whenCannotStart_showsToastAndStartsWork() {
        val urlsKey = "test_key"
        val fromSync = false

        mockkObject(DownloadUtils, recordPrivateCalls = true)
        every { DownloadUtils.canStartForegroundService(any()) } returns false
        every { DownloadUtils["startDownloadWork"](any<Context>(), any<String>(), any<Boolean>()) } returns Unit

        invokeStartDownloadServiceSafely(urlsKey, fromSync)

        verify(exactly = 0) { DownloadService.startService(any(), any(), any()) }
        verify(exactly = 1) { Utilities.toast(any(), any()) }
        verify(exactly = 1) { DownloadUtils["startDownloadWork"](context, urlsKey, fromSync) }
    }

    @Test
    fun testStartDownloadServiceSafely_whenCannotStartFromSync_doesNotShowToast() {
        val urlsKey = "test_key"
        val fromSync = true

        mockkObject(DownloadUtils, recordPrivateCalls = true)
        every { DownloadUtils.canStartForegroundService(any()) } returns false
        every { DownloadUtils["startDownloadWork"](any<Context>(), any<String>(), any<Boolean>()) } returns Unit

        invokeStartDownloadServiceSafely(urlsKey, fromSync)

        verify(exactly = 0) { DownloadService.startService(any(), any(), any()) }
        verify(exactly = 0) { Utilities.toast(any(), any()) }
        verify(exactly = 1) { DownloadUtils["startDownloadWork"](context, urlsKey, fromSync) }
    }
}
