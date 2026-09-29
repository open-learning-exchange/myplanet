package org.ole.planet.myplanet.ui.dashboard

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.OfflineActivity
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.collectEmissions

@OptIn(ExperimentalCoroutinesApi::class)
class ActivitiesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val userRepository = mockk<UserRepository>()
    private val activitiesRepository = mockk<ActivitiesRepository>()

    @Test
    fun `offlineLogins emits empty list and nothing else when user is null`() = runTest(testDispatcher) {
        coEvery { userRepository.getUserModel() } returns null

        val viewModel = ActivitiesViewModel(userRepository, activitiesRepository)

        val emissions = collectEmissions(viewModel.offlineLogins)

        assertEquals(1, emissions.size)
        assertEquals(emptyList<OfflineActivity>(), emissions[0])
    }

    @Test
    fun `offlineLogins emits data from repository when user is present`() = runTest(testDispatcher) {
        val userName = "testUser"
        val mockUser = mockk<UserEntity> {
            coEvery { name } returns userName
        }
        val mockActivities = listOf(OfflineActivity().apply { _id = "1" }, OfflineActivity().apply { _id = "2" })

        coEvery { userRepository.getUserModel() } returns mockUser
        every { activitiesRepository.getOfflineLogins(userName) } returns flowOf(mockActivities)

        val viewModel = ActivitiesViewModel(userRepository, activitiesRepository)

        val emissions = collectEmissions(viewModel.offlineLogins)

        // emissions[0] is the initial emptyList() due to StateFlow
        // emissions[1] is the mockActivities from the repository
        assertEquals(2, emissions.size)
        assertEquals(emptyList<OfflineActivity>(), emissions[0])
        assertEquals(mockActivities, emissions[1])
    }
}
