package org.ole.planet.myplanet.services

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.DownloadResult
import org.ole.planet.myplanet.repository.DownloadRepository
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ServiceController
import java.io.File
import java.lang.reflect.Field

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class DownloadServiceTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var serviceController: ServiceController<DownloadService>
    private lateinit var downloadService: DownloadService
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var broadcastService: BroadcastService

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        hiltRule.inject()
        Dispatchers.setMain(testDispatcher)

        sharedPreferences = mockk(relaxed = true)
        sharedPreferencesEditor = mockk(relaxed = true)
        every { sharedPreferences.edit() } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putStringSet(any(), any()) } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.remove(any()) } returns sharedPreferencesEditor

        downloadRepository = mockk(relaxed = true)
        broadcastService = mockk(relaxed = true)

        mockkStatic(DownloadUtils::class)
        every { DownloadUtils.createChannels(any()) } just Runs
        val mockNotification = mockk<Notification>(relaxed = true)
        every { DownloadUtils.buildInitialNotification(any()) } returns mockNotification
        every { DownloadUtils.buildCompletionNotification(any(), any(), any(), any(), any()) } returns mockNotification
        every { DownloadUtils.updateResourceOfflineStatus(any()) } just Runs

        mockkStatic(FileUtils::class)
        val tempFile = File.createTempFile("test", "file")
        every { FileUtils.getSDPathFromUrl(any(), any()) } returns tempFile
        every { FileUtils.getFileNameFromUrl(any()) } returns "testfile.txt"
        every { FileUtils.externalMemoryAvailable() } returns true
        every { FileUtils.availableExternalMemorySize } returns 1024L * 1024L * 100L // 100MB

        mockkObject(UrlUtils)
        every { UrlUtils.header } returns "Bearer token"

        mockkStatic("org.ole.planet.myplanet.di.BroadcastServiceEntryPointKt")
        every { org.ole.planet.myplanet.di.getBroadcastService(any()) } returns broadcastService

        serviceController = Robolectric.buildService(DownloadService::class.java)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val realPrefs = context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)
        realPrefs.edit().clear().commit()

        downloadService = serviceController.create().get()

        // Manual dependency injection for test
        downloadService.downloadRepository = downloadRepository

        // Ensure that `downloadScope` in `DownloadService` operates on the test dispatcher
        val field = DownloadService::class.java.getDeclaredField("downloadScope")
        field.isAccessible = true
        field.set(downloadService, kotlinx.coroutines.CoroutineScope(testDispatcher))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `test onStartCommand with empty queue stops service`() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val realPrefs = context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)
        realPrefs.edit().clear().commit()

        val intent = Intent()
        serviceController.startCommand(0, 1)

        advanceUntilIdle()

        assertTrue(shadowOf(downloadService).isStoppedBySelf)
    }

    @Test
    fun `test onStartCommand with priority queue processes url`() = runTest(testDispatcher) {
        val url = "http://example.com/file.txt"
        val urlSet = setOf(url)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val realPrefs = context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)
        realPrefs.edit().putStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, urlSet).commit()

        val responseBody = "test content".toResponseBody("text/plain".toMediaTypeOrNull())
        coEvery { downloadRepository.downloadFileResponse(url, any()) } returns DownloadResult.Success(responseBody)

        val intent = Intent()
        serviceController.startCommand(0, 1)

        advanceUntilIdle()

        coVerify { downloadRepository.downloadFileResponse(url, any()) }
        assertTrue(shadowOf(downloadService).isStoppedBySelf)
    }

    @Test
    fun `test onStartCommand with pending queue processes url`() = runTest(testDispatcher) {
        val url = "http://example.com/pending.txt"
        val urlSet = setOf(url)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val realPrefs = context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)
        realPrefs.edit().putStringSet(DownloadService.PENDING_DOWNLOADS_KEY, urlSet).commit()

        val responseBody = "test content".toResponseBody("text/plain".toMediaTypeOrNull())
        coEvery { downloadRepository.downloadFileResponse(url, any()) } returns DownloadResult.Success(responseBody)

        val intent = Intent()
        serviceController.startCommand(0, 1)

        advanceUntilIdle()

        coVerify { downloadRepository.downloadFileResponse(url, any()) }
        assertTrue(shadowOf(downloadService).isStoppedBySelf)
    }

    @Test
    fun `test download failure updates error and continues`() = runTest(testDispatcher) {
        val url = "http://example.com/fail.txt"
        val urlSet = setOf(url)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val realPrefs = context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)
        realPrefs.edit().putStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, urlSet).commit()

        coEvery { downloadRepository.downloadFileResponse(url, any()) } returns DownloadResult.Error("Not Found")

        val intent = Intent()
        serviceController.startCommand(0, 1)

        advanceUntilIdle()

        coVerify { downloadRepository.downloadFileResponse(url, any()) }
        assertTrue(shadowOf(downloadService).isStoppedBySelf)
    }
}
