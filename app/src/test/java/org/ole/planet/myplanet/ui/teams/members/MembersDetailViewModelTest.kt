package org.ole.planet.myplanet.ui.teams.members

import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertNull
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
    fun `visitStats is null until the repository answers`() = runTest {
        coEvery { activitiesRepository.getMemberVisitStats("user123", "john_doe") } returns MemberVisitStats(
            offlineVisitCount = 5,
            lastVisit = 1700000000000L
        )

        assertNull(viewModel.visitStats.value)

        viewModel.loadMemberVisitStats("user123", "john_doe")

        assertNull(viewModel.visitStats.value)
    }

    @Test
    fun `loadMemberVisitStats updates state flow when stats found for member`() = runTest {
        coEvery { activitiesRepository.getMemberVisitStats("user123", "john_doe") } returns MemberVisitStats(
            offlineVisitCount = 5,
            lastVisit = 1700000000000L
        )

        viewModel.loadMemberVisitStats("user123", "john_doe")
        testDispatcher.scheduler.advanceUntilIdle()

        val stats = viewModel.visitStats.value

        assertNotNull(stats)
        assertEquals("5", stats?.numberOfVisits)
        assertEquals("November 14, 2023 10:13 PM", stats?.lastLogin)
    }

    @Test
    fun `loadMemberVisitStats leaves lastLogin null when member has no recorded visits`() = runTest {
        coEvery { activitiesRepository.getMemberVisitStats("user456", "jane_doe") } returns MemberVisitStats(
            offlineVisitCount = 0,
            lastVisit = null
        )

        viewModel.loadMemberVisitStats("user456", "jane_doe")
        testDispatcher.scheduler.advanceUntilIdle()

        val stats = viewModel.visitStats.value

        assertNotNull(stats)
        assertEquals("0", stats?.numberOfVisits)
        assertNull(stats?.lastLogin)
    }

    @Test
    fun `loadMemberVisitStats leaves numberOfVisits null when memberId is missing`() = runTest {
        coEvery { activitiesRepository.getMemberVisitStats(null, "jane_doe") } returns MemberVisitStats(
            offlineVisitCount = 0,
            lastVisit = 1700000000000L
        )

        viewModel.loadMemberVisitStats(null, "jane_doe")
        testDispatcher.scheduler.advanceUntilIdle()

        val stats = viewModel.visitStats.value

        assertNotNull(stats)
        assertNull(stats?.numberOfVisits)
        assertEquals("November 14, 2023 10:13 PM", stats?.lastLogin)
    }

    @Test
    fun `loadMemberVisitStats does nothing when both memberId and username are missing`() = runTest {
        viewModel.loadMemberVisitStats(null, null)
        viewModel.loadMemberVisitStats("", "")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.visitStats.value)
        coVerify(exactly = 0) { activitiesRepository.getMemberVisitStats(any(), any()) }
    }

    @Test
    fun `loadMemberVisitStats only queries the repository once across view recreations`() = runTest {
        coEvery { activitiesRepository.getMemberVisitStats("user123", "john_doe") } returns MemberVisitStats(
            offlineVisitCount = 5,
            lastVisit = null
        )

        viewModel.loadMemberVisitStats("user123", "john_doe")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.loadMemberVisitStats("user123", "john_doe")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { activitiesRepository.getMemberVisitStats("user123", "john_doe") }
    }
}
