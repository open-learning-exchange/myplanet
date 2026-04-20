package org.ole.planet.myplanet.services

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import javax.inject.Inject
import javax.inject.Singleton
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

    companion object {
        private val testScheduler = TestCoroutineScheduler()
        private val ioDispatcher = StandardTestDispatcher(testScheduler)
        private val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns ioDispatcher
        }

        @Module
        @InstallIn(SingletonComponent::class)
        object TestModule {
            @Provides
            @Singleton
            fun provideDispatcherProvider(): DispatcherProvider = mockDispatcherProvider
        }
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
