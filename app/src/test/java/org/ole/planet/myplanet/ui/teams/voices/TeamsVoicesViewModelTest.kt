package org.ole.planet.myplanet.ui.teams.voices

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class TeamsVoicesViewModelTest {
    private lateinit var viewModel: TeamsVoicesViewModel
    private val voicesRepository: VoicesRepository = mockk(relaxed = true)
    private val teamsRepository: TeamsRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val dispatcherProvider: DispatcherProvider = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { dispatcherProvider.io } returns testDispatcher
        coEvery { dispatcherProvider.main } returns testDispatcher
        coEvery { dispatcherProvider.default } returns testDispatcher
        viewModel = TeamsVoicesViewModel(voicesRepository, teamsRepository, userRepository, dispatcherProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadTeam fetches team and maps to VoicePostingPolicy`() = runTest(testDispatcher) {
        val teamId = "team123"
        val mockTeam = mockk<RealmMyTeam>(relaxed = true)
        coEvery { mockTeam._id } returns teamId
        coEvery { mockTeam.isPublic } returns true
        coEvery { teamsRepository.getTeamByIdOrTeamId(teamId) } returns mockTeam

        viewModel.loadTeam(teamId)
        advanceUntilIdle()

        val (team, policy) = viewModel.teamPolicy.value
        assertEquals(mockTeam, team)
        assertEquals(teamId, policy?.teamId)
        assertEquals(true, policy?.isPublic)
    }
}
