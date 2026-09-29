package org.ole.planet.myplanet.ui.teams.members

import io.mockk.coEvery
import io.mockk.mockk
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.MemberVisitStats

@OptIn(ExperimentalCoroutinesApi::class)
class MembersDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var activitiesRepository: ActivitiesRepository
    private lateinit var viewModel: MembersDetailViewModel
    private lateinit var originalTimeZone: TimeZone
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        originalTimeZone = TimeZone.getDefault()
        originalLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)

        activitiesRepository = mockk()
        viewModel = MembersDetailViewModel(activitiesRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        TimeZone.setDefault(originalTimeZone)
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `loadMemberVisitStats updates state flow when stats found for member`() = runTest {
        coEvery { activitiesRepository.getMemberVisitStats("user123", "john_doe") } returns MemberVisitStats(
            offlineVisitCount = 5,
            lastVisit = 1700000000000L
        )

        viewModel.loadMemberVisitStats("user123", "john_doe")
        testDispatcher.scheduler.advanceUntilIdle()

        val stateMap = viewModel.visitStats.value
        val stats = stateMap["user123"]

        assertNotNull(stats)
        assertEquals("5", stats?.numberOfVisits)
        assertEquals("November 14, 2023 10:13 PM", stats?.lastLogin)
    }

    @Test
    fun `loadMemberVisitStats updates state flow when member has no recorded visits`() = runTest {
        coEvery { activitiesRepository.getMemberVisitStats("user456", "jane_doe") } returns MemberVisitStats(
            offlineVisitCount = 0,
            lastVisit = null
        )

        viewModel.loadMemberVisitStats("user456", "jane_doe")
        testDispatcher.scheduler.advanceUntilIdle()

        val stateMap = viewModel.visitStats.value
        val stats = stateMap["user456"]

        assertNotNull(stats)
        assertEquals("0", stats?.numberOfVisits)
        assertEquals("No logout record found", stats?.lastLogin)
    }

    @Test
    fun `loadMemberVisitStats does nothing when memberId is null or empty`() = runTest {
        viewModel.loadMemberVisitStats(null, "username")
        viewModel.loadMemberVisitStats("", "username")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyMap<String, MemberVisitStatsUiState>(), viewModel.visitStats.value)
    }
}
