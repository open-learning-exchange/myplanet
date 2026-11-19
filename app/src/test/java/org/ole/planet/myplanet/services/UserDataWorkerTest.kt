package org.ole.planet.myplanet.services

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.services.upload.UploadError
import org.ole.planet.myplanet.services.upload.UploadResult

@OptIn(ExperimentalCoroutinesApi::class)
class UserDataWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var uploadManager: UploadManager
    private lateinit var uploadToShelfService: UploadToShelfService
    private lateinit var healthRepository: HealthRepository
    private lateinit var worker: UserDataWorker

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        uploadManager = mockk(relaxed = true)
        uploadToShelfService = mockk(relaxed = true)
        healthRepository = mockk(relaxed = true)

        worker = UserDataWorker(
            appContext = context,
            workerParams = workerParams,
            uploadManager = uploadManager,
            uploadToShelfService = uploadToShelfService,
            healthRepository = healthRepository
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `doWork for UPLOAD_TYPE_LOGIN returns success with output data message`() = runTest {
        val inputData = Data.Builder()
            .putString(UserDataWorker.KEY_UPLOAD_TYPE, UserDataWorker.UPLOAD_TYPE_LOGIN)
            .build()
        every { workerParams.inputData } returns inputData

        coEvery { uploadManager.uploadUserActivities(any()) } answers {
            val listener = firstArg<OnSuccessListener>()
            listener.onSuccess("Login upload successful")
        }

        val result = worker.doWork()

        assertTrue(result is Result.Success)
        val successResult = result as Result.Success
        assertEquals(
            "Login upload successful",
            successResult.outputData.getString(UserDataWorker.KEY_SUCCESS_MESSAGE)
        )
        coVerify(exactly = 1) { uploadManager.uploadUserActivities(any()) }
    }

    @Test
    fun `doWork for UPLOAD_TYPE_BULK continues subsequent uploads even if one times out`() = runTest {
        val inputData = Data.Builder()
            .putString(UserDataWorker.KEY_UPLOAD_TYPE, UserDataWorker.UPLOAD_TYPE_BULK)
            .build()
        every { workerParams.inputData } returns inputData

        // 1. uploadToShelfService.uploadUserData completes
        coEvery { uploadToShelfService.uploadUserData(any()) } answers {
            val listener = firstArg<OnSuccessListener>()
            listener.onSuccess("Shelf sync ok")
        }

        // 2. uploadUserActivities times out (never invokes listener)
        coEvery { uploadManager.uploadUserActivities(any()) } answers {
            // Do not invoke listener to simulate timeout or stuck callback
        }

        // 3. uploadExamResult completes
        coEvery { uploadManager.uploadExamResult(any()) } answers {
            val listener = firstArg<OnSuccessListener>()
            listener.onSuccess("Exam result ok")
        }

        // 4. uploadFeedback returns true
        coEvery { uploadManager.uploadFeedback() } returns UploadResult.Empty

        // 5. uploadResource completes
        coEvery { uploadManager.uploadResource(any()) } answers {
            val listener = firstArg<OnSuccessListener?>()
            listener?.onSuccess("Resources ok")
        }

        // 6. uploadSubmitPhotos completes
        coEvery { uploadManager.uploadSubmitPhotos(any()) } answers {
            val listener = firstArg<OnSuccessListener?>()
            listener?.onSuccess("Photos ok")
        }

        // 7. uploadActivities completes
        coEvery { uploadManager.uploadActivities(any()) } answers {
            val listener = firstArg<OnSuccessListener?>()
            listener?.onSuccess("Activities ok")
        }

        val result = worker.doWork()

        // The stuck step is reported, so the user isn't told everything uploaded.
        assertTrue(result is Result.Failure)
        assertEquals(
            listOf("user activities"),
            (result as Result.Failure).outputData.getStringArray(UserDataWorker.KEY_FAILED_STEPS)?.toList()
        )

        // Verify that subsequent uploads were still executed in order
        coVerifyOrder {
            uploadToShelfService.uploadUserData(any())
            uploadManager.uploadUserActivities(any())
            uploadManager.uploadExamResult(any())
            uploadManager.uploadFeedback()
            uploadManager.uploadResource(any())
            uploadManager.uploadTeams()
            uploadManager.uploadSubmitPhotos(any())
            uploadManager.uploadActivities(any())
        }
    }

    @Test
    fun `doWork for UPLOAD_TYPE_BULK syncs pending health examinations once after the user data callback`() = runTest {
        val inputData = Data.Builder()
            .putString(UserDataWorker.KEY_UPLOAD_TYPE, UserDataWorker.UPLOAD_TYPE_BULK)
            .build()
        every { workerParams.inputData } returns inputData

        val events = mutableListOf<String>()
        coEvery { uploadToShelfService.uploadUserData(any()) } answers {
            val listener = firstArg<OnSuccessListener>()
            listener.onSuccess("Shelf sync ok")
            events.add("uploadUserDataCallback")
        }
        coEvery { healthRepository.syncPendingHealthExaminations() } answers {
            events.add("healthSync")
        }

        worker.doWork()

        coVerify(exactly = 1) { healthRepository.syncPendingHealthExaminations() }
        assertEquals(listOf("uploadUserDataCallback", "healthSync"), events)
    }

    @Test
    fun `doWork for UPLOAD_TYPE_BULK keeps going when the health sync throws but reports it`() = runTest {
        val inputData = Data.Builder()
            .putString(UserDataWorker.KEY_UPLOAD_TYPE, UserDataWorker.UPLOAD_TYPE_BULK)
            .build()
        every { workerParams.inputData } returns inputData

        coEvery { uploadToShelfService.uploadUserData(any()) } answers {
            val listener = firstArg<OnSuccessListener>()
            listener.onSuccess("Shelf sync ok")
        }
        coEvery { healthRepository.syncPendingHealthExaminations() } throws RuntimeException("health upload failed")
        stubCallbackStepsToComplete()

        val result = worker.doWork()

        assertTrue(result is Result.Failure)
        assertEquals(
            listOf("health examinations"),
            (result as Result.Failure).outputData.getStringArray(UserDataWorker.KEY_FAILED_STEPS)?.toList()
        )
        coVerify(exactly = 1) { healthRepository.syncPendingHealthExaminations() }
        coVerify(exactly = 1) { uploadManager.uploadFeedback() }
    }

    @Test
    fun `doWork for UPLOAD_TYPE_BULK rethrows CancellationException and stops subsequent uploads`() = runTest {
        val inputData = Data.Builder()
            .putString(UserDataWorker.KEY_UPLOAD_TYPE, UserDataWorker.UPLOAD_TYPE_BULK)
            .build()
        every { workerParams.inputData } returns inputData

        coEvery { uploadManager.uploadAchievement() } throws CancellationException("Worker stopped")

        try {
            worker.doWork()
            org.junit.Assert.fail("Expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("Worker stopped", e.message)
        }

        coVerify(exactly = 1) { uploadManager.uploadAchievement() }
        coVerify(exactly = 0) { uploadManager.uploadNews() }
        coVerify(exactly = 0) { uploadManager.uploadResourceActivities(any()) }
    }

    private fun bulkInput() {
        every { workerParams.inputData } returns Data.Builder()
            .putString(UserDataWorker.KEY_UPLOAD_TYPE, UserDataWorker.UPLOAD_TYPE_BULK)
            .build()
    }

    private fun stubCallbackStepsToComplete() {
        coEvery { uploadToShelfService.uploadUserData(any()) } answers { firstArg<OnSuccessListener>().onSuccess("ok") }
        coEvery { uploadManager.uploadUserActivities(any()) } answers { firstArg<OnSuccessListener>().onSuccess("ok") }
        coEvery { uploadManager.uploadExamResult(any()) } answers { firstArg<OnSuccessListener>().onSuccess("ok") }
        coEvery { uploadManager.uploadResource(any()) } answers { firstArg<OnSuccessListener?>()?.onSuccess("ok") }
        coEvery { uploadManager.uploadSubmitPhotos(any()) } answers { firstArg<OnSuccessListener?>()?.onSuccess("ok") }
        coEvery { uploadManager.uploadActivities(any()) } answers { firstArg<OnSuccessListener?>()?.onSuccess("ok") }
    }

    @Test
    fun `doWork for UPLOAD_TYPE_BULK succeeds when every step uploads`() = runTest {
        bulkInput()
        stubCallbackStepsToComplete()
        coEvery { uploadManager.uploadSubmissions(any()) } returns UploadResult.Empty

        assertEquals(Result.success(), worker.doWork())
    }

    @Test
    fun `doWork for UPLOAD_TYPE_BULK fails when the server rejects submissions`() = runTest {
        bulkInput()
        stubCallbackStepsToComplete()
        coEvery { uploadManager.uploadSubmissions(any()) } returns UploadResult.Failure(
            listOf(UploadError("sub1", java.io.IOException("server unreachable"), retryable = true))
        )

        val result = worker.doWork()

        assertTrue(result is Result.Failure)
        assertEquals(
            listOf("submissions"),
            (result as Result.Failure).outputData.getStringArray(UserDataWorker.KEY_FAILED_STEPS)?.toList()
        )
        // Later steps still run after a failure.
        coVerify(exactly = 1) { uploadManager.uploadActivities(any()) }
    }
}
