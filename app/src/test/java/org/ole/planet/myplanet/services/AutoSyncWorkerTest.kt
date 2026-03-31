package org.ole.planet.myplanet.services

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.slot
import java.util.Date
import java.util.concurrent.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utils.DialogUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope

@OptIn(ExperimentalCoroutinesApi::class)
class AutoSyncWorkerTest {

    private lateinit var worker: AutoSyncWorker
    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var preferences: SharedPreferences
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var syncManager: SyncManager
    private lateinit var uploadManager: UploadManager
    private lateinit var uploadToShelfService: UploadToShelfService
    private lateinit var configurationsRepository: ConfigurationsRepository

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope

    // Save original states
    private var originalSyncRunning = false
    private var originalSyncFailedCount = 0
    private var originalScope: CoroutineScope? = null
    private var originalContext: Context? = null

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        preferences = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)
        uploadManager = mockk(relaxed = true)
        uploadToShelfService = mockk(relaxed = true)
        configurationsRepository = mockk(relaxed = true)

        worker = spyk(AutoSyncWorker(context, workerParams, preferences, sharedPrefManager, syncManager, uploadManager, uploadToShelfService, configurationsRepository))

        mockkStatic(Utilities::class)
        every { Utilities.toast(any(), any()) } returns Unit

        testScope = TestScope(testDispatcher + Job())

        // Save state
        originalSyncRunning = MainApplication.isSyncRunning
        originalSyncFailedCount = MainApplication.syncFailedCount
        try {
            originalScope = MainApplication.applicationScope
        } catch (e: Exception) {
            // Ignored, might be uninitialized
        }
        try {
            originalContext = MainApplication.context
        } catch (e: Exception) {
            // Ignored, might be uninitialized
        }

        MainApplication.applicationScope = testScope
        MainApplication.isSyncRunning = false
        MainApplication.syncFailedCount = 0

        // Fix for uninitialized property context
        val mockContext = mockk<Application>(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getSharedPreferences(any(), any()) } returns mockk<SharedPreferences>(relaxed = true)
        MainApplication.context = mockContext
    }

    @After
    fun tearDown() {
        // Restore state
        MainApplication.isSyncRunning = originalSyncRunning
        MainApplication.syncFailedCount = originalSyncFailedCount
        if (originalScope != null) {
            MainApplication.applicationScope = originalScope!!
        }
        if (originalContext != null) {
            MainApplication.context = originalContext!!
        }

        testScope.cancel()
        unmockkAll()
    }

    @Test
    fun `doWork with stopped worker returns success`() = runTest {
        every { worker.isStopped } returns true

        val result = worker.doWork()

        assertTrue(result is Result.Success)
        verify(exactly = 0) { sharedPrefManager.getLastSync() }
    }

    @Test
    fun `doWork inside interval returns success`() = runTest {
        every { worker.isStopped } returns false
        val currentTime = System.currentTimeMillis()
        every { sharedPrefManager.getLastSync() } returns currentTime - 1000L // 1 sec ago
        every { sharedPrefManager.getAutoSyncInterval() } returns 3600 // 1 hr

        val result = worker.doWork()

        assertTrue(result is Result.Success)
        verify(exactly = 0) { configurationsRepository.checkVersion(any(), any()) }
    }

    @Test
    fun `doWork outside interval triggers checkVersion`() = runTest {
        every { worker.isStopped } returns false
        val currentTime = System.currentTimeMillis()
        every { sharedPrefManager.getLastSync() } returns currentTime - 4000000L // More than 1 hr ago
        every { sharedPrefManager.getAutoSyncInterval() } returns 3600 // 1 hr

        // Mock activity manager to return false for foreground
        val activityManager = mockk<ActivityManager>(relaxed = true)
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        every { activityManager.runningAppProcesses } returns null

        val result = worker.doWork()

        assertTrue(result is Result.Success)
        verify { configurationsRepository.checkVersion(worker, sharedPrefManager) }
    }

    @Test
    fun `onSyncFailed does not start LoginActivity when syncFailedCount is 3 or less`() {
        MainApplication.syncFailedCount = 3

        every { context.startActivity(any()) } returns Unit

        worker.onSyncFailed("error")

        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun `onSyncFailed starts LoginActivity when syncFailedCount greater than 3`() {
        MainApplication.syncFailedCount = 4

        io.mockk.mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } returns io.mockk.mockk<Intent>(relaxed = true)
        every { anyConstructed<Intent>().setFlags(any()) } returns io.mockk.mockk<Intent>(relaxed = true)

        every { context.startActivity(any()) } returns Unit

        worker.onSyncFailed("error")

        verify { context.startActivity(any()) }
    }

    @Test
    fun `onUpdateAvailable triggers startDownloadUpdate`() {
        mockkStatic(DialogUtils::class)
        mockkObject(UrlUtils)

        val mockInfo = mockk<MyPlanet>()
        every { mockInfo.localapkpath } returns "testpath"

        every { UrlUtils.getApkUpdateUrl(any()) } returns "http://testurl"
        every { DialogUtils.startDownloadUpdate(any(), any(), any(), any(), any()) } returns Unit

        worker.onUpdateAvailable(mockInfo, true)

        verify { DialogUtils.startDownloadUpdate(context, "http://testurl", null, MainApplication.applicationScope, configurationsRepository) }
    }

    @Test
    fun `onError with blockSync false triggers upload operations`() = runTest(testDispatcher) {
        // Setup configurationsRepository.checkHealth to invoke callback
        val checkHealthSlot = slot<org.ole.planet.myplanet.callback.OnSuccessListener>()
        every { configurationsRepository.checkHealth(capture(checkHealthSlot)) } answers {
            checkHealthSlot.captured.onSuccess("success")
        }

        // Setup uploadToShelfService.uploadUserData to invoke callback
        val uploadUserDataSlot = slot<org.ole.planet.myplanet.callback.OnSuccessListener>()
        every { uploadToShelfService.uploadUserData(capture(uploadUserDataSlot)) } answers {
            uploadUserDataSlot.captured.onSuccess("success")
        }

        worker.onError("error", false)

        verify { syncManager.start(worker, "upload") }
        verify { uploadToShelfService.uploadUserData(any()) }
        verify { configurationsRepository.checkHealth(any()) }
        verify { uploadToShelfService.uploadHealth() }

        advanceUntilIdle() // Process coroutines in testScope

        coVerify { uploadManager.uploadExamResult(worker) }
        coVerify { uploadManager.uploadFeedback() }
        coVerify { uploadManager.uploadAchievement() }
        coVerify { uploadManager.uploadResourceActivities("") }
        coVerify { uploadManager.uploadUserActivities(worker) }
        coVerify { uploadManager.uploadCourseActivities() }
        coVerify { uploadManager.uploadSearchActivity() }
        coVerify { uploadManager.uploadRating() }
        coVerify { uploadManager.uploadResource(worker) }
        coVerify { uploadManager.uploadNews() }
        coVerify { uploadManager.uploadTeams() }
        coVerify { uploadManager.uploadTeamTask() }
        coVerify { uploadManager.uploadMeetups() }
        coVerify { uploadManager.uploadAdoptedSurveys() }
        coVerify { uploadManager.uploadCrashLog() }
        coVerify { uploadManager.uploadSubmissions() }
        coVerify { uploadManager.uploadActivities(null) }
    }

    @Test
    fun `onSuccess updates last usage`() {
        worker.onSuccess("success")
        verify { sharedPrefManager.setLastUsageUploaded(any()) }
    }
}
