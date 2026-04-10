package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class TeamsRepositoryImplTest {

    private lateinit var teamsRepository: TeamsRepositoryImpl
    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val userSessionManager: UserSessionManager = mockk(relaxed = true)
    private val uploadManager: UploadManager = mockk(relaxed = true)
    private val gson: Gson = mockk(relaxed = true)
    private val preferences: SharedPreferences = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val serverUrlMapper: ServerUrlMapper = mockk(relaxed = true)
    private val dispatcherProvider: DispatcherProvider = mockk()
    private val apiInterfaceMock = mockk<org.ole.planet.myplanet.data.api.ApiInterface>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { dispatcherProvider.main } returns testDispatcher
        every { dispatcherProvider.io } returns testDispatcher
        every { dispatcherProvider.default } returns testDispatcher
        every { dispatcherProvider.unconfined } returns testDispatcher

        // Mock ServerUrlMapper behavior used in syncTeamActivities
        val serverUrlMapping = mockk<org.ole.planet.myplanet.services.sync.ServerUrlMapper.UrlMapping>(relaxed = true)
        every { serverUrlMapping.primaryUrl } returns "http://primary.com"
        every { serverUrlMapping.alternativeUrl } returns null
        coEvery { serverUrlMapper.processUrl(any()) } returns serverUrlMapping
        every { sharedPrefManager.getServerUrl() } returns "http://test.com"

        val mockUserRepository = mockk<UserRepository>(relaxed = true)

        teamsRepository = TeamsRepositoryImpl(
            databaseService,
            UnconfinedTestDispatcher(),
            userSessionManager,
            uploadManager,
            gson,
            preferences,
            sharedPrefManager,
            serverUrlMapper,
            dispatcherProvider,
            apiInterfaceMock,
            mockUserRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `test syncTeamActivities uses dispatcherProvider io`() = runTest(testDispatcher) {
        // We will mock MainApplication object to avoid real network call
        io.mockk.mockkObject(org.ole.planet.myplanet.MainApplication.Companion)
        coEvery { org.ole.planet.myplanet.MainApplication.Companion.isServerReachable(any()) } returns true

        coEvery { uploadManager.uploadResource(any()) } returns Unit
        coEvery { uploadManager.uploadTeams() } returns Unit
        coEvery { uploadManager.uploadTeamActivities(any()) } returns Unit

        teamsRepository.syncTeamActivities()

        advanceUntilIdle()

        // Verify that the methods on uploadManager were called
        coVerify { uploadManager.uploadResource(null) }
        coVerify { uploadManager.uploadTeams() }
        coVerify { uploadManager.uploadTeamActivities(apiInterfaceMock) }

        // Unmock static objects
        io.mockk.unmockkObject(org.ole.planet.myplanet.MainApplication.Companion)
    }
}
