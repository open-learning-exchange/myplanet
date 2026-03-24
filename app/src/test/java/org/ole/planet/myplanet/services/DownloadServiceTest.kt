package org.ole.planet.myplanet.services

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.android.controller.ServiceController
import java.io.File
import java.lang.reflect.Field

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class DownloadServiceTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var serviceController: ServiceController<DownloadService>
    private lateinit var downloadService: DownloadService
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var notificationManager: NotificationManager
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

        notificationManager = mockk(relaxed = true)
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

        // Build service properly using Robolectric
        serviceController = Robolectric.buildService(DownloadService::class.java)
        downloadService = serviceController.get()

        // Inject dependencies into the real Hilt-managed service
        downloadService.downloadRepository = downloadRepository

        // Mock internal context dependencies
        val spiedService = spyk(downloadService)
        every { spiedService.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { spiedService.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        every { spiedService.startForeground(any(), any()) } just Runs
        every { spiedService.stopSelf() } just Runs
        every { spiedService.stopForeground(any<Int>()) } just Runs
        every { spiedService.getString(any()) } returns "Downloading..."

        // Inject the spy back into our reference and replace the actual context calls.
        downloadService = spiedService

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
        every { sharedPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, any()) } returns emptySet()
        every { sharedPreferences.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, any()) } returns emptySet()

        val intent = Intent()
        downloadService.onStartCommand(intent, 0, 1)

        advanceUntilIdle()

        verify { downloadService.stopSelf() }
    }

    @Test
    fun `test onStartCommand with priority queue processes url`() = runTest(testDispatcher) {
        val url = "http://example.com/file.txt"
        val urlSet = setOf(url)
        every { sharedPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, any()) } returnsMany listOf(urlSet, emptySet())
        every { sharedPreferences.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, any()) } returns emptySet()
        every { sharedPreferencesEditor.putStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, any()) } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putStringSet(DownloadService.PENDING_DOWNLOADS_KEY, any()) } returns sharedPreferencesEditor

        val responseBody = "test content".toResponseBody("text/plain".toMediaTypeOrNull())
        coEvery { downloadRepository.downloadFileResponse(url, any()) } returns DownloadResult.Success(responseBody)

        val intent = Intent()
        downloadService.onStartCommand(intent, 0, 1)

        advanceUntilIdle()

        coVerify { downloadRepository.downloadFileResponse(url, any()) }
        verify(atLeast = 1) { downloadService.stopSelf() }
    }

    @Test
    fun `test onStartCommand with pending queue processes url`() = runTest(testDispatcher) {
        val url = "http://example.com/pending.txt"
        val urlSet = setOf(url)
        every { sharedPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, any()) } returns emptySet()
        every { sharedPreferences.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, any()) } returnsMany listOf(urlSet, emptySet())
        every { sharedPreferencesEditor.putStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, any()) } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putStringSet(DownloadService.PENDING_DOWNLOADS_KEY, any()) } returns sharedPreferencesEditor

        val responseBody = "test content".toResponseBody("text/plain".toMediaTypeOrNull())
        coEvery { downloadRepository.downloadFileResponse(url, any()) } returns DownloadResult.Success(responseBody)

        val intent = Intent()
        downloadService.onStartCommand(intent, 0, 1)

        advanceUntilIdle()

        coVerify { downloadRepository.downloadFileResponse(url, any()) }
        verify(atLeast = 1) { downloadService.stopSelf() }
    }

    @Test
    fun `test download failure updates error and continues`() = runTest(testDispatcher) {
        val url = "http://example.com/fail.txt"
        val urlSet = setOf(url)
        every { sharedPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, any()) } returnsMany listOf(urlSet, emptySet())
        every { sharedPreferences.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, any()) } returns emptySet()
        every { sharedPreferencesEditor.putStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, any()) } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putStringSet(DownloadService.PENDING_DOWNLOADS_KEY, any()) } returns sharedPreferencesEditor

        coEvery { downloadRepository.downloadFileResponse(url, any()) } returns DownloadResult.Error("Not Found")

        val intent = Intent()
        downloadService.onStartCommand(intent, 0, 1)

        advanceUntilIdle()

        coVerify { downloadRepository.downloadFileResponse(url, any()) }
        verify(atLeast = 1) { downloadService.stopSelf() }
    }
}
