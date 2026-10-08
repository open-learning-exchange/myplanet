package org.ole.planet.myplanet.services

import android.content.SharedPreferences
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.di.DispatcherModule
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.ServerUrlMapper.UrlMapping
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.ServerReachabilityProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@UninstallModules(DispatcherModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SubmissionsUploaderTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val testScheduler = TestCoroutineScheduler()
    private val ioDispatcher = StandardTestDispatcher(testScheduler)

    @BindValue
    @JvmField
    val dispatcherProvider: DispatcherProvider = mockk(relaxed = true) {
        every { io } returns ioDispatcher
    }

    @BindValue
    @JvmField
    val uploadManager: UploadManager = mockk(relaxed = true)

    @BindValue
    @JvmField
    val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)

    @BindValue
    @JvmField
    val serverUrlMapper: ServerUrlMapper = mockk(relaxed = true)

    @BindValue
    @JvmField
    val serverReachabilityProvider: ServerReachabilityProvider = mockk(relaxed = true)

    @Inject
    lateinit var submissionsUploader: SubmissionsUploader

    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private val rawPrefs: SharedPreferences = mockk(relaxed = true)

    @Before
    fun setUp() {
        hiltRule.inject()

        every { sharedPrefManager.getServerUrl() } returns "http://primary.com"
        every { sharedPrefManager.rawPreferences } returns rawPrefs
        every { rawPrefs.edit() } returns editor

        every { serverUrlMapper.processUrl("http://primary.com") } returns UrlMapping(
            primaryUrl = "http://primary.com",
            alternativeUrl = "http://alt.com"
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun checkAvailableServer_whenPrimaryIsReachable_uploadsSubmissionsAndDoesNotUpdatePreferences() = runTest(testScheduler) {
        coEvery { serverReachabilityProvider.isServerReachable("http://primary.com") } returns true
        coEvery { serverReachabilityProvider.isServerReachable("http://alt.com") } returns false

        submissionsUploader.checkAvailableServer(12345L)
        advanceUntilIdle()

        coVerify { uploadManager.uploadAdoptedSurveys() }
        coVerify { uploadManager.uploadSubmissions(12345L) }
        verify(exactly = 0) {
            serverUrlMapper.updateUrlPreferences(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun checkAvailableServer_whenPrimaryDownAndAlternativeReachable_switchesUrlAndUploadsSubmissions() = runTest(testScheduler) {
        coEvery { serverReachabilityProvider.isServerReachable("http://primary.com") } returns false
        coEvery { serverReachabilityProvider.isServerReachable("http://alt.com") } returns true

        submissionsUploader.checkAvailableServer(12345L)
        advanceUntilIdle()

        verify {
            serverUrlMapper.updateUrlPreferences(
                editor,
                any(),
                "http://alt.com",
                "http://primary.com",
                rawPrefs
            )
        }
        coVerify { uploadManager.uploadAdoptedSurveys() }
        coVerify { uploadManager.uploadSubmissions(12345L) }
    }

    @Test
    fun checkAvailableServer_whenBothDown_skipsUploadAndDoesNotUpdatePreferences() = runTest(testScheduler) {
        coEvery { serverReachabilityProvider.isServerReachable("http://primary.com") } returns false
        coEvery { serverReachabilityProvider.isServerReachable("http://alt.com") } returns false

        submissionsUploader.checkAvailableServer(12345L)
        advanceUntilIdle()

        coVerify(exactly = 0) { uploadManager.uploadAdoptedSurveys() }
        coVerify(exactly = 0) { uploadManager.uploadSubmissions(any()) }
        verify(exactly = 0) {
            serverUrlMapper.updateUrlPreferences(any(), any(), any(), any(), any())
        }
    }
}
