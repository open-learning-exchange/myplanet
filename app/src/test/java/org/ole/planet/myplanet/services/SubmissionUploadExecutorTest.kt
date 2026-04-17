package org.ole.planet.myplanet.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.utils.DispatcherProvider
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionUploadExecutorTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testExecute_invokesBlockOnCorrectDispatcher() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider>()
        every { mockDispatcherProvider.io } returns ioDispatcher

        val executor = SubmissionUploadExecutor(this, mockDispatcherProvider)

        var invoked = false
        var capturedDispatcher: CoroutineDispatcher? = null

        executor.execute {
            invoked = true
            capturedDispatcher = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        }

        advanceUntilIdle()

        assertTrue(invoked)
        assertEquals(ioDispatcher, capturedDispatcher)
    }
}
