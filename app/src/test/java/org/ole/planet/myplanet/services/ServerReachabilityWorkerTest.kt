package org.ole.planet.myplanet.services

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider

@OptIn(ExperimentalCoroutinesApi::class)
class ServerReachabilityWorkerTest {

    private val context: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val uploadManager: UploadManager = mockk(relaxed = true)
    private val submissionsRepository: SubmissionsRepository = mockk(relaxed = true)
    private val serverUrlMapper: ServerUrlMapper = mockk(relaxed = true)
    private val syncManager: SyncManager = mockk(relaxed = true)
    private val timeProvider = TestTimeProvider(NOW)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockkObject(NetworkUtils)
        every { NetworkUtils.isNetworkConnected } returns true

        mockkObject(MainApplication.Companion)

        every { context.applicationContext } returns context
        every { workerParams.inputData } returns Data.EMPTY
        every { sharedPrefManager.getServerUrl() } returns SERVER_URL
        every { syncManager.isMainSyncActive() } returns false
        // Hold the notification cooldown shut so no test wanders into the notification builder.
        every { sharedPrefManager.getRawLong(any(), any()) } returns NOW
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createWorker(dispatcher: TestDispatcher) = ServerReachabilityWorker(
        context,
        workerParams,
        sharedPrefManager,
        uploadManager,
        submissionsRepository,
        serverUrlMapper,
        TestDispatcherProvider(dispatcher),
        timeProvider,
        syncManager
    )

    @Test
    fun `doWork rethrows cancellation instead of returning retry`() = runTest {
        every { sharedPrefManager.getServerUrl() } throws CancellationException(STOPPED)
        val worker = createWorker(UnconfinedTestDispatcher(testScheduler))

        try {
            val result = worker.doWork()
            fail("expected the cancellation to propagate, but doWork returned $result")
        } catch (e: CancellationException) {
            assertEquals(STOPPED, e.message)
        }

        verify(exactly = 0) { Log.w(any<String>(), any<String>(), any<Throwable>()) }
    }

    @Test
    fun `doWork cancelled mid-check does not start an upload`() = runTest {
        every { workerParams.inputData } returns workDataOf(NETWORK_RECONNECTION_KEY to true)
        every { serverUrlMapper.processUrl(SERVER_URL) } returns
            ServerUrlMapper.UrlMapping(SERVER_URL, null, SERVER_URL)
        // Reachable on the initial probe, cancelled on the re-check inside checkAvailableServerAndUpload.
        coEvery { MainApplication.isServerReachable(SERVER_URL) } returns true andThenThrows
            CancellationException(STOPPED)
        val worker = createWorker(UnconfinedTestDispatcher(testScheduler))

        try {
            val result = worker.doWork()
            fail("expected the cancellation to propagate, but doWork returned $result")
        } catch (e: CancellationException) {
            assertEquals(STOPPED, e.message)
        }

        coVerify(exactly = 0) { submissionsRepository.hasPendingOfflineSubmissions() }
        coVerify(exactly = 0) { uploadManager.uploadSubmissions(any()) }
    }

    @Test
    fun `doWork still logs and retries when an ordinary exception escapes`() = runTest {
        every { sharedPrefManager.getServerUrl() } throws IllegalStateException("boom")
        val worker = createWorker(UnconfinedTestDispatcher(testScheduler))

        assertEquals(Result.retry(), worker.doWork())

        verify(exactly = 1) {
            Log.w("ServerReachabilityWorker", "doWork failed", any<IllegalStateException>())
        }
    }

    @Test
    fun `doWork succeeds without touching the server when the network is down`() = runTest {
        every { NetworkUtils.isNetworkConnected } returns false
        val worker = createWorker(UnconfinedTestDispatcher(testScheduler))

        assertEquals(Result.success(), worker.doWork())

        verify(exactly = 0) { sharedPrefManager.getServerUrl() }
    }

    companion object {
        private const val SERVER_URL = "http://server.example"
        private const val STOPPED = "worker stopped"
        private const val NOW = 1_700_000_000_000L
        private const val NETWORK_RECONNECTION_KEY = "network_reconnection_trigger"
    }
}
