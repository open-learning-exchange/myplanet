package org.ole.planet.myplanet.ui.user

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.gamification.BadgeCategory
import org.ole.planet.myplanet.model.gamification.GamificationBadge
import org.ole.planet.myplanet.model.gamification.GamificationSummary
import org.ole.planet.myplanet.model.gamification.StudyStreakInfo
import org.ole.planet.myplanet.repository.GamificationRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class GamificationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val gamificationRepository: GamificationRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)

    private lateinit var viewModel: GamificationViewModel

    private val mockBadges = listOf(
        GamificationBadge("b1", "Badge 1", "Desc", BadgeCategory.COURSES, "🎓", 1, 1, true),
        GamificationBadge("b2", "Badge 2", "Desc", BadgeCategory.STREAKS, "🔥", 3, 7, false),
        GamificationBadge("b3", "Badge 3", "Desc", BadgeCategory.TEAMS, "🤝", 1, 1, true)
    )

    private val mockSummary = GamificationSummary(
        streakInfo = StudyStreakInfo(currentStreak = 3, longestStreak = 5, isActiveToday = true),
        badges = mockBadges,
        certificates = emptyList(),
        totalBadgesCount = 3,
        unlockedBadgesCount = 2,
        completedCoursesCount = 1,
        resourcesReadCount = 10L,
        tasksCompletedCount = 1
    )

    @Before
    fun setUp() {
        coEvery { gamificationRepository.getGamificationSummary("user1", "Alex") } returns mockSummary
        viewModel = GamificationViewModel(gamificationRepository, userRepository)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun loadGamificationData_updatesGamificationSummary() = runTest {
        viewModel.loadGamificationData("user1", "Alex")
        advanceUntilIdle()

        val summary = viewModel.gamificationSummary.value
        assertNotNull(summary)
        assertEquals(3, summary?.streakInfo?.currentStreak)
        assertEquals(3, summary?.badges?.size)
        coVerify { gamificationRepository.getGamificationSummary("user1", "Alex") }
    }

    @Test
    fun setCategory_filtersBadgesCorrectly() = runTest {
        viewModel.loadGamificationData("user1", "Alex")
        advanceUntilIdle()

        assertEquals(BadgeCategory.ALL, viewModel.selectedCategory.value)
        assertEquals(3, viewModel.filteredBadges.value.size)

        viewModel.setCategory(BadgeCategory.COURSES)
        advanceUntilIdle()

        assertEquals(BadgeCategory.COURSES, viewModel.selectedCategory.value)
        assertEquals(1, viewModel.filteredBadges.value.size)
        assertEquals("b1", viewModel.filteredBadges.value.first().id)

        viewModel.setCategory(BadgeCategory.STREAKS)
        advanceUntilIdle()
        assertEquals(1, viewModel.filteredBadges.value.size)
        assertEquals("b2", viewModel.filteredBadges.value.first().id)
    }
}
