package org.ole.planet.myplanet.ui.ratings

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.MainDispatcherRule
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.RatingEntry
import org.ole.planet.myplanet.repository.RatingSummary
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import kotlinx.coroutines.Dispatchers

@ExperimentalCoroutinesApi
class RatingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var ratingsRepository: RatingsRepository
    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: RatingsViewModel

    @Before
    fun setup() {
        ratingsRepository = mockk()
        userRepository = mockk()
        val testDispatcherProvider = object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
            override val unconfined = Dispatchers.Unconfined
        }
        viewModel = RatingsViewModel(ratingsRepository, userRepository, testDispatcherProvider)
    }

    @Test
    fun `getRatingSummary returns success state`() = runTest {
        val type = "course"
        val itemId = "item-1"
        val userId = "user-1"

        val mockUser = RealmUser().apply { id = userId }
        val mockSummary = RatingSummary(
            existingRating = RatingEntry("r-1", "Good", 5),
            averageRating = 4.5f,
            totalRatings = 10,
            userRating = 5
        )

        coEvery { userRepository.getUserById(userId) } returns mockUser
        coEvery { ratingsRepository.getRatingSummary(type, itemId, userId) } returns mockSummary

        viewModel.loadRatingData(type, itemId, userId)
        advanceUntilIdle()

        val state = viewModel.ratingState.value
        assertTrue(state is RatingsViewModel.RatingUiState.Success)
        val successState = state as RatingsViewModel.RatingUiState.Success
        assertEquals(mockSummary.existingRating, successState.existingRating)
        assertEquals(mockSummary.averageRating, successState.averageRating)
        assertEquals(mockSummary.totalRatings, successState.totalRatings)
        assertEquals(mockSummary.userRating, successState.userRating)
    }

    @Test
    fun `getRatingSummary returns error state`() = runTest {
        val type = "course"
        val itemId = "item-1"
        val userId = "user-1"

        val mockUser = RealmUser().apply { id = userId }

        coEvery { userRepository.getUserById(userId) } returns mockUser
        coEvery { ratingsRepository.getRatingSummary(type, itemId, userId) } throws RuntimeException("fail")

        viewModel.loadRatingData(type, itemId, userId)
        advanceUntilIdle()

        val state = viewModel.ratingState.value
        assertTrue(state is RatingsViewModel.RatingUiState.Error)
        val errorState = state as RatingsViewModel.RatingUiState.Error
        assertEquals("fail", errorState.message)
    }

    @Test
    fun `userState is populated on success path`() = runTest {
        val type = "course"
        val itemId = "item-1"
        val userId = "user-1"

        val mockUser = RealmUser().apply { id = userId }
        val mockSummary = RatingSummary(
            existingRating = RatingEntry("r-1", "Good", 5),
            averageRating = 4.5f,
            totalRatings = 10,
            userRating = 5
        )

        coEvery { userRepository.getUserById(userId) } returns mockUser
        coEvery { ratingsRepository.getRatingSummary(type, itemId, userId) } returns mockSummary

        viewModel.loadRatingData(type, itemId, userId)
        advanceUntilIdle()

        assertEquals(mockUser, viewModel.userState.value)
    }

    @Test
    fun `submitRating success path`() = runTest {
        val type = "course"
        val itemId = "item-1"
        val title = "Title"
        val userId = "user-1"
        val rating = 4.5f
        val comment = "Great"

        val mockUser = RealmUser().apply { id = userId }
        val mockSummary = RatingSummary(
            existingRating = RatingEntry("r-1", "Good", 5),
            averageRating = 4.5f,
            totalRatings = 10,
            userRating = 5
        )

        coEvery { userRepository.getUserById(userId) } returns mockUser
        coEvery { userRepository.getValidUserId(mockUser, userId) } returns userId
        coEvery { ratingsRepository.submitRating(type, itemId, title, userId, rating, comment) } returns mockSummary

        viewModel.submitRating(type, itemId, title, userId, rating, comment)
        advanceUntilIdle()

        val state = viewModel.submitState.value
        assertTrue(state is RatingsViewModel.SubmitState.Success)
    }
}
