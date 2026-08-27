package org.ole.planet.myplanet.services

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.DownloadResult
import org.ole.planet.myplanet.repository.DownloadRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.UrlUtils

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadWorkerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var broadcastService: BroadcastService
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var resourcesRepository: ResourcesRepository
    private lateinit var preferences: SharedPreferences
    private lateinit var worker: DownloadWorker

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        broadcastService = mockk(relaxed = true)
        resourcesRepository = mockk(relaxed = true)
        preferences = mockk(relaxed = true)

        dispatcherProvider = mockk(relaxed = true)
        every { dispatcherProvider.io } returns testDispatcher
        every { dispatcherProvider.main } returns testDispatcher
        every { dispatcherProvider.default } returns testDispatcher
        every { dispatcherProvider.unconfined } returns testDispatcher

        mockkStatic(Log::class)
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        every { context.getString(any()) } returns ""
        every { context.getString(any(), any(), any()) } returns ""
        every { context.getString(any(), any<String>()) } returns ""
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns mockk<NotificationManager>(relaxed = true)

        worker = spyk(
            DownloadWorker(
                context,
                workerParams,
                downloadRepository,
                broadcastService,
                dispatcherProvider,
                resourcesRepository,
                preferences
            )
        )
        coEvery { worker.setProgress(any()) } returns Unit
        coEvery { worker.setForeground(any()) } returns Unit

        mockkObject(DownloadUtils)
        every { DownloadUtils.createChannels(any()) } returns Unit
        every { DownloadUtils.buildProgressNotification(any(), any(), any(), any(), any(), any()) } returns mockk<Notification>(relaxed = true)
        every { DownloadUtils.buildCompletionNotification(any(), any(), any(), any(), any()) } returns mockk<Notification>(relaxed = true)

        mockkObject(UrlUtils)
        every { UrlUtils.header } returns "Basic dGVzdDp0ZXN0"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `doWork returns failure when url set is empty`() = runTest(testDispatcher) {
        every { preferences.getStringSet(any(), any()) } returns emptySet()

        val result = worker.doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Failure)
    }

    @Test
    fun `downloadFile failure path logs tagged error when download throws`() = runTest(testDispatcher) {
        val url = "http://example.com/resources/123/file.txt"
        every { preferences.getStringSet(any(), any()) } returns setOf(url)
        every { workerParams.inputData.getString("urls_key") } returns "url_list_key"
        every { workerParams.inputData.getBoolean("fromSync", false) } returns false

        mockkObject(FileUtils)
        every { FileUtils.checkFileExist(context, url) } returns false

        coEvery { downloadRepository.downloadFileResponse(any(), any()) } throws RuntimeException("boom")

        val result = worker.doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        verify(atLeast = 1) { Log.e("DownloadWorker", any(), any<Throwable>()) }

        unmockkObject(FileUtils)
    }

    @Test
    fun `doWork returns success even when an individual download reports an error`() = runTest(testDispatcher) {
        val url = "http://example.com/resources/123/file.txt"
        every { preferences.getStringSet(any(), any()) } returns setOf(url)
        every { workerParams.inputData.getString("urls_key") } returns "url_list_key"
        every { workerParams.inputData.getBoolean("fromSync", false) } returns false

        mockkObject(FileUtils)
        every { FileUtils.checkFileExist(context, url) } returns false
        every { FileUtils.getSDPathFromUrl(context, url) } returns mockk(relaxed = true)

        coEvery { downloadRepository.downloadFileResponse(any(), any()) } returns DownloadResult.Error("err", 500)

        val result = worker.doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)

        unmockkObject(FileUtils)
    }
}
