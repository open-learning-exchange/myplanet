package org.ole.planet.myplanet.ui.dashboard

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.Calendar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.OfflineActivity
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.collectEmissions

@OptIn(ExperimentalCoroutinesApi::class)
class ActivitiesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val userRepository = mockk<UserRepository>()
    private val activitiesRepository = mockk<ActivitiesRepository>()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Test
    fun `offlineLogins emits empty list and nothing else when user is null`() = runTest(testDispatcher) {
        coEvery { userRepository.getUserModel() } returns null

        val viewModel = ActivitiesViewModel(userRepository, activitiesRepository, dispatcherProvider)

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

        val viewModel = ActivitiesViewModel(userRepository, activitiesRepository, dispatcherProvider)

        val emissions = collectEmissions(viewModel.offlineLogins)

        // emissions[0] is the initial emptyList() due to StateFlow
        // emissions[1] is the mockActivities from the repository
        assertEquals(2, emissions.size)
        assertEquals(emptyList<OfflineActivity>(), emissions[0])
        assertEquals(mockActivities, emissions[1])
    }

    @Test
    fun computeMonthlyCounts_emptyList_returnsEmptyMap() = runTest(testDispatcher) {
        val startMillis = 1000L
        val endMillis = 5000L

        val viewModel = ActivitiesViewModel(userRepository, activitiesRepository, dispatcherProvider)

        val result = viewModel.computeMonthlyCounts(emptyList(), startMillis, endMillis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun computeMonthlyCounts_filtersOutOfRangeLoginsAndGroupsCorrectly() = runTest(testDispatcher) {
        val calendar = Calendar.getInstance()

        // Month 0 (January)
        calendar.set(2026, Calendar.JANUARY, 15, 12, 0, 0)
        val janTime1 = calendar.timeInMillis
        calendar.set(2026, Calendar.JANUARY, 20, 14, 0, 0)
        val janTime2 = calendar.timeInMillis

        // Month 2 (March)
        calendar.set(2026, Calendar.MARCH, 10, 10, 0, 0)
        val marchTime = calendar.timeInMillis

        // Out of range (earlier)
        calendar.set(2024, Calendar.JANUARY, 1, 0, 0, 0)
        val outOfRangeTime = calendar.timeInMillis

        val logins = listOf(
            OfflineActivity().apply { id = "1"; userName = "test"; loginTime = janTime1 },
            OfflineActivity().apply { id = "2"; userName = "test"; loginTime = janTime2 },
            OfflineActivity().apply { id = "3"; userName = "test"; loginTime = marchTime },
            OfflineActivity().apply { id = "4"; userName = "test"; loginTime = outOfRangeTime },
            OfflineActivity().apply { id = "5"; userName = "test"; loginTime = null }
        )

        calendar.set(2025, Calendar.DECEMBER, 31, 23, 59, 59)
        val startMillis = calendar.timeInMillis
        calendar.set(2026, Calendar.DECEMBER, 31, 23, 59, 59)
        val endMillis = calendar.timeInMillis

        val viewModel = ActivitiesViewModel(userRepository, activitiesRepository, dispatcherProvider)

        val result = viewModel.computeMonthlyCounts(logins, startMillis, endMillis)

        assertEquals(2, result.size)
        assertEquals(2, result[Calendar.JANUARY])
        assertEquals(1, result[Calendar.MARCH])
    }

    @Test
    fun `monthlyLoginCounts emits aggregated map when offlineLogins emits list`() = runTest(testDispatcher) {
        val userName = "testUser"
        val mockUser = mockk<UserEntity> {
            coEvery { name } returns userName
        }
        val loginTime = Calendar.getInstance().timeInMillis
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val mockActivities = listOf(
            OfflineActivity().apply { id = "1"; this.userName = userName; this.loginTime = loginTime }
        )

        coEvery { userRepository.getUserModel() } returns mockUser
        every { activitiesRepository.getOfflineLogins(userName) } returns flowOf(mockActivities)

        val viewModel = ActivitiesViewModel(userRepository, activitiesRepository, dispatcherProvider)

        val emissions = collectEmissions(viewModel.monthlyLoginCounts)

        // emissions[0] is emptyMap() initial value
        // emissions[1] is map containing currentMonth -> 1
        assertEquals(2, emissions.size)
        assertEquals(emptyMap<Int, Int>(), emissions[0])
        assertEquals(mapOf(currentMonth to 1), emissions[1])
    }
}
