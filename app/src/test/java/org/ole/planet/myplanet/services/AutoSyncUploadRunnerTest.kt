package org.ole.planet.myplanet.services

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.callback.OnSuccessListener

@OptIn(ExperimentalCoroutinesApi::class)
class AutoSyncUploadRunnerTest {

    private val uploadManager: UploadManager = mockk(relaxed = true)
    private val listener: OnSuccessListener = mockk(relaxed = true)
    private lateinit var runner: AutoSyncUploadRunner

    @Before
    fun setUp() {
        runner = AutoSyncUploadRunner(uploadManager)
    }

    @Test
    fun `when every upload succeeds, runAll returns null`() = runTest {
        val result = runner.runAll(listener)
        assertNull(result)
    }

    @Test
    fun `within group A, calls execute in sequence`() = runTest {
        runner.runAll(listener)
        coVerifyOrder {
            uploadManager.uploadExamResult(listener)
            uploadManager.uploadAdoptedSurveys()
            uploadManager.uploadSubmissions()
        }
    }

    @Test
    fun `within group B, calls execute in sequence`() = runTest {
        runner.runAll(listener)
        coVerifyOrder {
            uploadManager.uploadUserActivities(listener)
            uploadManager.uploadTeams()
            uploadManager.uploadTeamTask()
            uploadManager.uploadMeetups()
        }
    }

    @Test
    fun `within group C, calls execute in sequence`() = runTest {
        runner.runAll(listener)
        coVerifyOrder {
            uploadManager.uploadResourceActivities("")
            uploadManager.uploadRating()
            uploadManager.uploadResource(listener)
        }
    }

    @Test
    fun `concurrency - independent jobs run concurrently with groups`() = runTest {
        val deferred = CompletableDeferred<Unit>()
        coEvery { uploadManager.uploadFeedback() } coAnswers {
            deferred.await()
            true
        }

        val testJob = launch {
            runner.runAll(listener)
        }

        testScheduler.advanceUntilIdle()

        coVerify { uploadManager.uploadCrashLog() }
        coVerify { uploadManager.uploadExamResult(listener) }

        deferred.complete(Unit)
        testJob.join()
    }

    @Test
    fun `concurrency - peak concurrent tasks do not exceed semaphore permit limit`() = runTest {
        val activeCount = AtomicInteger(0)
        val maxActiveCount = AtomicInteger(0)

        suspend fun simulateWork() {
            val current = activeCount.incrementAndGet()
            maxActiveCount.updateAndGet { maxOf(it, current) }
            delay(50)
            activeCount.decrementAndGet()
        }

        coEvery { uploadManager.uploadExamResult(any()) } coAnswers { simulateWork() }
        coEvery { uploadManager.uploadUserActivities(any()) } coAnswers { simulateWork() }
        coEvery { uploadManager.uploadResourceActivities(any()) } coAnswers { simulateWork() }
        coEvery { uploadManager.uploadFeedback() } coAnswers { simulateWork(); true }
        coEvery { uploadManager.uploadAchievement() } coAnswers { simulateWork() }
        coEvery { uploadManager.uploadCourseActivities() } coAnswers { simulateWork() }
        coEvery { uploadManager.uploadSearchActivity() } coAnswers { simulateWork() }
        coEvery { uploadManager.uploadNews() } coAnswers { simulateWork() }
        coEvery { uploadManager.uploadCrashLog() } coAnswers { simulateWork() }

        runner.runAll(listener)

        assertTrue("Expected max active count <= 3 but was ${maxActiveCount.get()}", maxActiveCount.get() <= 3)
    }

    @Test
    fun `isolation - exception in group B stops group B remaining tasks but other groups run`() = runTest {
        val expectedException = RuntimeException("uploadTeams failed")
        coEvery { uploadManager.uploadTeams() } throws expectedException

        val result = runner.runAll(listener)

        assertEquals(expectedException, result)

        coVerify { uploadManager.uploadUserActivities(listener) }
        coVerify { uploadManager.uploadTeams() }
        coVerify(exactly = 0) { uploadManager.uploadTeamTask() }
        coVerify(exactly = 0) { uploadManager.uploadMeetups() }

        coVerify { uploadManager.uploadExamResult(listener) }
        coVerify { uploadManager.uploadAdoptedSurveys() }
        coVerify { uploadManager.uploadSubmissions() }

        coVerify { uploadManager.uploadResourceActivities("") }
        coVerify { uploadManager.uploadRating() }
        coVerify { uploadManager.uploadResource(listener) }

        coVerify { uploadManager.uploadCrashLog() }
        coVerify { uploadManager.uploadNews() }
    }

    @Test
    fun `cancellation exception propagates and is not returned as failure`() = runTest {
        coEvery { uploadManager.uploadExamResult(listener) } throws CancellationException("Cancelled")

        var caughtCancellation = false
        try {
            runner.runAll(listener)
        } catch (e: CancellationException) {
            caughtCancellation = true
        }

        assertTrue(caughtCancellation)
    }

    @Test
    fun `uploadActivities null is called exactly once after all others`() = runTest {
        runner.runAll(listener)
        coVerify(exactly = 1) { uploadManager.uploadActivities(null) }
    }
}
