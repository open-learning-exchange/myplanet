package org.ole.planet.myplanet.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.DispatcherProvider
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionUploadExecutorTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val ioDispatcher = StandardTestDispatcher()
    private lateinit var mockDispatcherProvider: DispatcherProvider
    private lateinit var executor: SubmissionUploadExecutor

    @Before
    fun setUp() {
        mockDispatcherProvider = mockk()
        every { mockDispatcherProvider.io } returns ioDispatcher
        executor = SubmissionUploadExecutor(testScope, mockDispatcherProvider)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testExecute_invokesBlockOnCorrectDispatcher() = testScope.runTest {
        var invoked = false
        var capturedDispatcher: CoroutineDispatcher? = null

        executor.execute {
            invoked = true
            capturedDispatcher = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        }

        ioDispatcher.scheduler.advanceUntilIdle()

        assertTrue(invoked)
        assertEquals(ioDispatcher, capturedDispatcher)
    }
}
