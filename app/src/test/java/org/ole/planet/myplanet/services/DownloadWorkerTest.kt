package org.ole.planet.myplanet.services

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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

    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
        every { DownloadUtils.canStartForegroundService(any()) } returns true
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

        // The only download failed, so the run is retried rather than reported as a success
        assertTrue(result is androidx.work.ListenableWorker.Result.Retry)
        verify(atLeast = 1) { Log.e("DownloadWorker", any(), any<Throwable>()) }

        unmockkObject(FileUtils)
    }

    @Test
    fun `doWork retries when every download reports an error`() = runTest(testDispatcher) {
        val url = "http://example.com/resources/123/file.txt"
        every { preferences.getStringSet(any(), any()) } returns setOf(url)
        every { workerParams.inputData.getString("urls_key") } returns "url_list_key"
        every { workerParams.inputData.getBoolean("fromSync", false) } returns false

        mockkObject(FileUtils)
        every { FileUtils.checkFileExist(context, url) } returns false
        every { FileUtils.getSDPathFromUrl(context, url) } returns mockk(relaxed = true)

        coEvery { downloadRepository.downloadFileResponse(any(), any()) } returns DownloadResult.Error("err", 500)

        val result = worker.doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Retry)

        unmockkObject(FileUtils)
    }

    @Test
    fun `showProgressNotification reuses foreground promotion and calls canStartForegroundService only once across multiple ticks`() = runTest(testDispatcher) {
        val url = "http://example.com/resources/123/file.txt"
        every { preferences.getStringSet(any(), any()) } returns setOf(url)
        every { workerParams.inputData.getString("urls_key") } returns "url_list_key"
        every { workerParams.inputData.getBoolean("fromSync", false) } returns false

        every { DownloadUtils.canStartForegroundService(any()) } returns true

        mockkObject(FileUtils)
        every { FileUtils.checkFileExist(context, url) } returns false
        val tempFile = java.io.File.createTempFile("test_download", ".tmp")
        tempFile.deleteOnExit()
        every { FileUtils.getSDPathFromUrl(context, url) } returns tempFile

        mockkStatic(android.os.SystemClock::class)
        every { android.os.SystemClock.elapsedRealtime() } returnsMany listOf(1000L, 1600L, 2200L, 2800L, 3400L)

        val bodyData = ByteArray(8192 * 3)
        val responseBody = bodyData.toResponseBody(null)
        coEvery { downloadRepository.downloadFileResponse(any(), any()) } returns DownloadResult.Success(responseBody, 200)

        val result = worker.doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        verify(exactly = 1) { DownloadUtils.canStartForegroundService(context) }
        coVerify(atLeast = 3) { worker.setForeground(any()) }

        unmockkObject(FileUtils)
        io.mockk.unmockkStatic(android.os.SystemClock::class)
    }

    @Test
    fun `showProgressNotification retries canStartForegroundService on subsequent tick when setForeground fails`() = runTest(testDispatcher) {
        val url = "http://example.com/resources/123/file.txt"
        every { preferences.getStringSet(any(), any()) } returns setOf(url)
        every { workerParams.inputData.getString("urls_key") } returns "url_list_key"
        every { workerParams.inputData.getBoolean("fromSync", false) } returns false

        every { DownloadUtils.canStartForegroundService(any()) } returns true
        coEvery { worker.setForeground(any()) } throws RuntimeException("Foreground promotion failed")

        mockkObject(FileUtils)
        every { FileUtils.checkFileExist(context, url) } returns false
        val tempFile = java.io.File.createTempFile("test_download_fail", ".tmp")
        tempFile.deleteOnExit()
        every { FileUtils.getSDPathFromUrl(context, url) } returns tempFile

        mockkStatic(android.os.SystemClock::class)
        every { android.os.SystemClock.elapsedRealtime() } returnsMany listOf(1000L, 1600L, 2200L, 2800L)

        val bodyData = ByteArray(8192 * 3)
        val responseBody = bodyData.toResponseBody(null)
        coEvery { downloadRepository.downloadFileResponse(any(), any()) } returns DownloadResult.Success(responseBody, 200)

        val result = worker.doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        verify(atLeast = 2) { DownloadUtils.canStartForegroundService(context) }

        unmockkObject(FileUtils)
        io.mockk.unmockkStatic(android.os.SystemClock::class)
    }

    @Test
    fun `doWork rethrows CancellationException when cancelled during download`() = runTest(testDispatcher) {
        val url1 = "http://example.com/file1.txt"
        val url2 = "http://example.com/file2.txt"
        val url3 = "http://example.com/file3.txt"
        every { preferences.getStringSet(any(), any()) } returns setOf(url1, url2, url3)
        every { workerParams.inputData.getString("urls_key") } returns "url_list_key"
        every { workerParams.inputData.getBoolean("fromSync", false) } returns false

        mockkObject(FileUtils)
        every { FileUtils.checkFileExist(context, any()) } returns false
        val dir = java.nio.file.Files.createTempDirectory("download_worker").toFile()
        every { FileUtils.getSDPathFromUrl(context, any()) } answers { java.io.File(dir, secondArg<String>().substringAfterLast('/')) }

        coEvery { downloadRepository.downloadFileResponse(url1, any()) } coAnswers {
            awaitCancellation()
        }

        val job = launch {
            worker.doWork()
        }

        testScheduler.advanceUntilIdle()
        job.cancel()
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.downloadFileResponse(any(), any()) }
        coVerify(exactly = 0) { downloadRepository.downloadFileResponse(url2, any()) }
        coVerify(exactly = 0) { downloadRepository.downloadFileResponse(url3, any()) }

        unmockkObject(FileUtils)
    }

    private fun stubSingleDownload(url: String): java.io.File {
        every { preferences.getStringSet(any(), any()) } returns setOf(url)
        every { workerParams.inputData.getString("urls_key") } returns "url_list_key"
        every { workerParams.inputData.getBoolean("fromSync", false) } returns false
        mockkObject(FileUtils)
        every { FileUtils.checkFileExist(context, url) } returns false
        val dir = java.nio.file.Files.createTempDirectory("download_worker").toFile()
        val outputFile = java.io.File(dir, "file.txt")
        every { FileUtils.getSDPathFromUrl(context, url) } returns outputFile
        return outputFile
    }

    @Test
    fun `a partial file is resumed with a range request and appended to`() = runTest(testDispatcher) {
        val url = "http://example.com/resources/123/file.txt"
        val outputFile = stubSingleDownload(url)
        val dir = outputFile.parentFile!!
        java.io.File(dir, "file.txt.tmp").writeText("first-half-")
        java.io.File(dir, "file.txt.tmp.etag").writeText("\"etag-1\"")
        coEvery { downloadRepository.downloadFileResponse(url, any(), 11L, "\"etag-1\"") } returns
            DownloadResult.Success("second-half".toByteArray().toResponseBody(null), 206, "\"etag-1\"")

        worker.doWork()

        assertEquals("first-half-second-half", outputFile.readText())
        assertTrue(!java.io.File(dir, "file.txt.tmp").exists())
        assertTrue(!java.io.File(dir, "file.txt.tmp.etag").exists())
        assertTrue(!java.io.File(dir, "file.txt.tmp.size").exists())
        coVerify { resourcesRepository.markResourceOfflineByUrl(url) }
        dir.deleteRecursively()
    }

    @Test
    fun `a full response to a resume replaces the stale partial file`() = runTest(testDispatcher) {
        val url = "http://example.com/resources/123/file.txt"
        val outputFile = stubSingleDownload(url)
        val dir = outputFile.parentFile!!
        java.io.File(dir, "file.txt.tmp").writeText("stale")
        coEvery { downloadRepository.downloadFileResponse(url, any(), 5L, null) } returns
            DownloadResult.Success("fresh-whole-file".toByteArray().toResponseBody(null), 200)

        worker.doWork()

        assertEquals("fresh-whole-file", outputFile.readText())
        dir.deleteRecursively()
    }

    @Test
    fun `a rejected resume offset restarts the download from the first byte`() = runTest(testDispatcher) {
        val url = "http://example.com/resources/123/file.txt"
        val outputFile = stubSingleDownload(url)
        val dir = outputFile.parentFile!!
        java.io.File(dir, "file.txt.tmp").writeText("mismatched")
        java.io.File(dir, "file.txt.tmp.etag").writeText("\"old\"")
        coEvery { downloadRepository.downloadFileResponse(url, any(), 10L, "\"old\"") } returns
            DownloadResult.Error("Requested range not satisfiable", 416)
        coEvery { downloadRepository.downloadFileResponse(url, any(), 0L, null) } returns
            DownloadResult.Success("fresh-whole-file".toByteArray().toResponseBody(null), 200)

        val result = worker.doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        assertEquals("fresh-whole-file", outputFile.readText())
        assertTrue(!java.io.File(dir, "file.txt.tmp.etag").exists())
        dir.deleteRecursively()
    }

    @Test
    fun `resultFor succeeds on any success, retries a total failure, then gives up`() {
        assertTrue(DownloadWorker.resultFor(1, 3, runAttemptCount = 0) is androidx.work.ListenableWorker.Result.Success)
        assertTrue(DownloadWorker.resultFor(0, 3, runAttemptCount = 0) is androidx.work.ListenableWorker.Result.Retry)
        assertTrue(
            DownloadWorker.resultFor(0, 3, runAttemptCount = DownloadWorker.MAX_RETRY_ATTEMPTS)
                is androidx.work.ListenableWorker.Result.Failure
        )
    }

    @Test
    fun `complete download is moved into place without leaving a part file`() = runTest(testDispatcher) {
        val url = "http://example.com/resources/123/file.txt"
        every { preferences.getStringSet(any(), any()) } returns setOf(url)
        every { workerParams.inputData.getString("urls_key") } returns "url_list_key"
        every { workerParams.inputData.getBoolean("fromSync", false) } returns false

        mockkObject(FileUtils)
        every { FileUtils.checkFileExist(context, url) } returns false
        val dir = java.nio.file.Files.createTempDirectory("download_worker").toFile()
        val outputFile = java.io.File(dir, "file.txt")
        every { FileUtils.getSDPathFromUrl(context, url) } returns outputFile

        val bodyData = ByteArray(1024) { it.toByte() }
        coEvery { downloadRepository.downloadFileResponse(any(), any()) } returns DownloadResult.Success(bodyData.toResponseBody(null), 200)

        worker.doWork()

        assertTrue(outputFile.readBytes().contentEquals(bodyData))
        assertTrue(!java.io.File(dir, "file.txt.part").exists())

        dir.deleteRecursively()
        unmockkObject(FileUtils)
    }

    @Test
    fun `truncated download keeps its partial file for resume and is not marked offline`() = runTest(testDispatcher) {
        val url = "http://example.com/resources/123/file.txt"
        every { preferences.getStringSet(any(), any()) } returns setOf(url)
        every { workerParams.inputData.getString("urls_key") } returns "url_list_key"
        every { workerParams.inputData.getBoolean("fromSync", false) } returns false

        mockkObject(FileUtils)
        every { FileUtils.checkFileExist(context, url) } returns false
        val dir = java.nio.file.Files.createTempDirectory("download_worker").toFile()
        val outputFile = java.io.File(dir, "file.txt")
        every { FileUtils.getSDPathFromUrl(context, url) } returns outputFile

        val truncatedBody = object : okhttp3.ResponseBody() {
            override fun contentType(): okhttp3.MediaType? = null
            override fun contentLength(): Long = 4096
            override fun source(): okio.BufferedSource = okio.Buffer().write(ByteArray(1024))
        }
        coEvery { downloadRepository.downloadFileResponse(any(), any()) } returns DownloadResult.Success(truncatedBody, 200)

        worker.doWork()

        assertTrue(!outputFile.exists())
        assertEquals("the bytes received so far are kept for the next attempt", 1024L, java.io.File(dir, "file.txt.tmp").length())
        assertEquals("4096", java.io.File(dir, "file.txt.tmp.size").readText())
        coVerify(exactly = 0) { resourcesRepository.markResourceOfflineByUrl(url) }

        dir.deleteRecursively()
        unmockkObject(FileUtils)
    }

    @Test
    fun `successful downloads are removed from the queue and failed ones stay`() = runTest(testDispatcher) {
        val done = "http://example.com/resources/1/done.txt"
        val failed = "http://example.com/resources/2/failed.txt"
        val queued = setOf(done, failed)
        every { preferences.getStringSet(any(), any()) } returns queued
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { preferences.edit() } returns editor
        every { editor.putStringSet(any(), any()) } returns editor
        every { workerParams.inputData.getString("urls_key") } returns "url_list_key"
        every { workerParams.inputData.getBoolean("fromSync", false) } returns false

        mockkObject(FileUtils)
        // An already-downloaded file counts as a success without hitting the network
        every { FileUtils.checkFileExist(context, done) } returns true
        every { FileUtils.checkFileExist(context, failed) } returns false
        every { FileUtils.getSDPathFromUrl(context, failed) } returns mockk(relaxed = true)
        coEvery { downloadRepository.downloadFileResponse(failed, any()) } returns DownloadResult.Error("err", 500)

        val result = worker.doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        verify { editor.putStringSet("url_list_key", setOf(failed)) }

        unmockkObject(FileUtils)
    }

    @Test
    fun `a stopped worker stops reading the file instead of streaming it to the end`() = runTest(testDispatcher) {
        val url = "http://example.com/resources/123/video.mp4"
        every { preferences.getStringSet(any(), any()) } returns setOf(url)
        every { workerParams.inputData.getString("urls_key") } returns "url_list_key"
        every { workerParams.inputData.getBoolean("fromSync", false) } returns false

        val outputFile = File(temporaryFolder.newFolder("res"), "video.mp4")
        mockkObject(FileUtils)
        every { FileUtils.checkFileExist(any(), url) } returns false
        every { FileUtils.getSDPathFromUrl(any(), url) } returns outputFile

        val notificationManager = mockk<NotificationManager>(relaxed = true)
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        val stoppable = spyk(
            DownloadWorker(context, workerParams, downloadRepository, broadcastService, dispatcherProvider, resourcesRepository, preferences)
        )
        coEvery { stoppable.setForeground(any()) } returns Unit

        val stopJob = Job()
        var reads = 0
        val endless = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = -1L
            override fun source(): BufferedSource = object : Source {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    reads++
                    if (reads == 3) stopJob.cancel()
                    if (reads > 1_000) return -1L
                    sink.write(ByteArray(1024))
                    return 1024
                }
                override fun timeout(): Timeout = Timeout.NONE
                override fun close() {}
            }.buffer()
        }
        coEvery { downloadRepository.downloadFileResponse(url, any()) } returns DownloadResult.Success(endless, 200)

        val outcome = runCatching { withContext(stopJob) { stoppable.doWork() } }

        assertTrue("a stopped worker must end with cancellation", outcome.exceptionOrNull() is CancellationException)
        assertEquals("no more chunks may be read once stopped", 3, reads)
        verify { notificationManager.cancel(DownloadWorker.WORKER_NOTIFICATION_ID) }
        coVerify(exactly = 0) { broadcastService.sendBroadcast(any()) }
    }
}
