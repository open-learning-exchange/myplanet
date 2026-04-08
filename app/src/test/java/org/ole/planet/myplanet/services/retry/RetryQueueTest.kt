package org.ole.planet.myplanet.services.retry

import android.content.Context
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.repository.retry.RetryRepository

@OptIn(ExperimentalCoroutinesApi::class)
class RetryQueueTest {

    @MockK
    lateinit var retryRepository: RetryRepository

    @MockK
    lateinit var context: Context

    private lateinit var retryQueue: RetryQueue

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        retryQueue = RetryQueue(retryRepository, context)
    }

    @Test
    fun recoverStuckOperations_delegatesToRepository() = runTest {
        coEvery { retryRepository.recoverStuckOperations() } returns Unit

        retryQueue.recoverStuckOperations()

        coVerify(exactly = 1) { retryRepository.recoverStuckOperations() }
    }

    @Test
    fun getPendingOperations_delegatesToRepository() = runTest {
        coEvery { retryRepository.getPending() } returns listOf()

        retryQueue.getPendingOperations()

        coVerify(exactly = 1) { retryRepository.getPending() }
    }

    @Test
    fun markInProgress_delegatesToRepository() = runTest {
        val operationId = "test_op_id"
        coEvery { retryRepository.markInProgress(operationId) } returns Unit

        retryQueue.markInProgress(operationId)

        coVerify(exactly = 1) { retryRepository.markInProgress(operationId) }
    }

    @Test
    fun cleanup_delegatesToRepository() = runTest {
        coEvery { retryRepository.cleanup() } returns Unit

        retryQueue.cleanup()

        coVerify(exactly = 1) { retryRepository.cleanup() }
    }
}
