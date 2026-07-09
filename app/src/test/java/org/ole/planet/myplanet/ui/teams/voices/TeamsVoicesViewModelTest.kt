package org.ole.planet.myplanet.ui.teams.voices

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
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
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setup() {
        viewModel = TeamsVoicesViewModel(voicesRepository, teamsRepository, userRepository, dispatcherProvider)
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

        val (team, policy) = viewModel.teamPolicy.value!!
        assertEquals(mockTeam, team)
        assertEquals(teamId, policy?.teamId)
        assertEquals(true, policy?.isPublic)
    }
}
