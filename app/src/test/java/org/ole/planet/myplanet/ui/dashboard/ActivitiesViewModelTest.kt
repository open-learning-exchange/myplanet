package org.ole.planet.myplanet.ui.dashboard

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.OfflineActivity
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.services.UserSessionManager

@OptIn(ExperimentalCoroutinesApi::class)
class ActivitiesViewModelTest {

    private val userSessionManager = mockk<UserSessionManager>()
    private val activitiesRepository = mockk<ActivitiesRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `offlineLogins emits empty list and nothing else when user is null`() = runTest {
        coEvery { userSessionManager.getUserModel() } returns null

        val viewModel = ActivitiesViewModel(userSessionManager, activitiesRepository)

        val emissions = mutableListOf<List<OfflineActivity>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.offlineLogins.toList(emissions)
        }

        advanceUntilIdle()

        assertEquals(1, emissions.size)
        assertEquals(emptyList<OfflineActivity>(), emissions[0])

        job.cancel()
    }

    @Test
    fun `offlineLogins emits data from repository when user is present`() = runTest {
        val userName = "testUser"
        val mockUser = mockk<UserEntity> {
            coEvery { name } returns userName
        }
        val mockActivities = listOf(OfflineActivity().apply { _id = "1" }, OfflineActivity().apply { _id = "2" })

        coEvery { userSessionManager.getUserModel() } returns mockUser
        coEvery { activitiesRepository.getOfflineLogins(userName) } returns flowOf(mockActivities)

        val viewModel = ActivitiesViewModel(userSessionManager, activitiesRepository)

        val emissions = mutableListOf<List<OfflineActivity>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.offlineLogins.toList(emissions)
        }

        advanceUntilIdle()

        // emissions[0] is the initial emptyList() due to StateFlow
        // emissions[1] is the mockActivities from the repository
        assertEquals(2, emissions.size)
        assertEquals(emptyList<OfflineActivity>(), emissions[0])
        assertEquals(mockActivities, emissions[1])

        job.cancel()
    }
}
