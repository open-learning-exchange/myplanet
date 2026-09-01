package org.ole.planet.myplanet.ui.teams.voices

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class TeamsVoicesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var viewModel: TeamsVoicesViewModel
    private val voicesRepository: VoicesRepository = mockk(relaxed = true)
    private val teamsRepository: TeamsRepository = mockk(relaxed = true)
    private val userRepository: org.ole.planet.myplanet.repository.UserRepository = mockk(relaxed = true)
    private val resourcesRepository: org.ole.planet.myplanet.repository.ResourcesRepository = mockk(relaxed = true)
    private val notificationsRepository: org.ole.planet.myplanet.repository.NotificationsRepository = mockk(relaxed = true)
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setup() {
        viewModel = TeamsVoicesViewModel(voicesRepository, teamsRepository, dispatcherProvider, userRepository, resourcesRepository, notificationsRepository)
    }

    @Test
    fun `loadTeam fetches team and maps to VoicePostingPolicy`() = runTest(testDispatcher) {
        val teamId = "team123"
        val mockTeam = MyTeam().apply {
            _id = teamId
            isPublic = true
        }
        coEvery { teamsRepository.getTeamByIdOrTeamId(teamId) } returns mockTeam

        viewModel.loadTeam(teamId)
        advanceUntilIdle()

        val (team, policy) = viewModel.teamPolicy.value!!
        assertEquals(mockTeam, team)
        assertEquals(teamId, policy?.teamId)
        assertEquals(true, policy?.isPublic)
    }

    @Test
    fun `getFilteredNews writes newsList size as the watermark and returns the list`() = runTest(testDispatcher) {
        val teamId = "team123"
        val newsList = listOf<News>(mockk(relaxed = true), mockk(relaxed = true))
        coEvery { voicesRepository.getFilteredNews(teamId) } returns newsList

        val result = viewModel.getFilteredNews(teamId)

        assertEquals(newsList, result)
        coVerify(exactly = 1) { notificationsRepository.updateTeamNotification(teamId, newsList.size) }
        coVerify(exactly = 0) { voicesRepository.countTopLevelByTeam(teamId) }
        coVerify(exactly = 0) { voicesRepository.countTeamChats(teamId) }
    }
}
