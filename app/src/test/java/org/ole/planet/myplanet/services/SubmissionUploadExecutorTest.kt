package org.ole.planet.myplanet.services

import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.di.DispatcherModule
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@UninstallModules(DispatcherModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class SubmissionUploadExecutorTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val testScheduler = TestCoroutineScheduler()
    private val ioDispatcher = StandardTestDispatcher(testScheduler)

    @BindValue
    @JvmField
    val dispatcherProvider: DispatcherProvider = mockk(relaxed = true) {
        every { io } returns ioDispatcher
    }

    @Inject
    lateinit var executor: SubmissionUploadExecutor

    @Before
    fun init() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testExecutorHiltWiringAndDispatcherUsage() = runTest(testScheduler) {
        var invoked = false
        var capturedDispatcher: CoroutineDispatcher? = null

        executor.execute {
            invoked = true
            capturedDispatcher = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        }

        advanceUntilIdle()

        assertTrue("Block should have been invoked", invoked)
        assertEquals("Block should run on the provided IO dispatcher", ioDispatcher, capturedDispatcher)
    }
}
