package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestTimeProvider

class TeamsRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var teamsRepository: TeamsRepositoryImpl
    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val userSessionManager: UserSessionManager = mockk(relaxed = true)
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val uploadManager: UploadManager = mockk(relaxed = true)
    private val gson: Gson = mockk(relaxed = true)
    private val preferences: SharedPreferences = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val serverUrlMapper: ServerUrlMapper = mockk(relaxed = true)
    private val dispatcherProvider: DispatcherProvider = mockk()
    private val apiInterfaceMock = mockk<ApiInterface>(relaxed = true)

    private val testDispatcher = mainDispatcherRule.testDispatcher

    @Before
    fun setup() {
        every { dispatcherProvider.main } returns testDispatcher
        every { dispatcherProvider.io } returns testDispatcher
        every { dispatcherProvider.default } returns testDispatcher
        every { dispatcherProvider.unconfined } returns testDispatcher

        // Mock ServerUrlMapper behavior used in syncTeamActivities
        val serverUrlMapping = mockk<ServerUrlMapper.UrlMapping>(relaxed = true)
        every { serverUrlMapping.primaryUrl } returns "http://primary.com"
        every { serverUrlMapping.alternativeUrl } returns null
        coEvery { serverUrlMapper.processUrl(any()) } returns serverUrlMapping
        every { sharedPrefManager.getServerUrl() } returns "http://test.com"

        val mockUserRepository = mockk<UserRepository>(relaxed = true)

        teamsRepository = TeamsRepositoryImpl(
            activitiesRepository,
            databaseService,
            mainDispatcherRule.testDispatcher,
            userSessionManager,
            uploadManager,
            gson,
            preferences,
            sharedPrefManager,
            serverUrlMapper,
            dispatcherProvider,
            mockUserRepository,
            mockk(),
            TestTimeProvider()
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test refreshJoinedMembersForLogin uses getJoinedMembers and saves users`() = runTest(testDispatcher) {
        val teamId = "test_team_id"
        val mockUser = RealmUser().apply {
            name = "Test User"
            userImage = "http://example.com/image.png"
        }
        val mockTeamMembers = listOf(mockUser)

        val spyRepository = io.mockk.spyk(teamsRepository)
        coEvery { spyRepository.getJoinedMembers(teamId) } returns mockTeamMembers

        val existingSavedUsers = emptyList<User>()
        every { sharedPrefManager.getSavedUsers() } returns existingSavedUsers

        every { sharedPrefManager.setSavedUsers(any()) } returns Unit

        val result = spyRepository.refreshJoinedMembersForLogin(teamId)

        org.junit.Assert.assertEquals(mockTeamMembers, result)

        io.mockk.verify {
            sharedPrefManager.getSavedUsers()
            sharedPrefManager.setSavedUsers(match { list ->
                list.size == 1 && list[0].name == "Test User" && list[0].image == "http://example.com/image.png" && list[0].source == "team"
            })
        }
    }

    @Test
    fun `test syncTeamActivities uses dispatcherProvider io`() = runTest(testDispatcher) {
        // We will mock MainApplication object to avoid real network call
        io.mockk.mockkObject(org.ole.planet.myplanet.MainApplication.Companion)
        coEvery { org.ole.planet.myplanet.MainApplication.Companion.isServerReachable(any()) } returns true

        coEvery { uploadManager.uploadResource(any()) } returns Unit
        coEvery { uploadManager.uploadTeams() } returns Unit
        coEvery { uploadManager.uploadTeamActivities() } returns Unit

        teamsRepository.syncTeamActivities()

        advanceUntilIdle()

        // Verify that the methods on uploadManager were called
        coVerify { uploadManager.uploadResource(null) }
        coVerify { uploadManager.uploadTeams() }
        coVerify { uploadManager.uploadTeamActivities() }

        // Unmock static objects
        io.mockk.unmockkObject(org.ole.planet.myplanet.MainApplication.Companion)
    }
}
