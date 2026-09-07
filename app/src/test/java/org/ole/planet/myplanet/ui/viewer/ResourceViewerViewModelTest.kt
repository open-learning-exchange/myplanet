package org.ole.planet.myplanet.ui.viewer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.RatingSummary
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ResourceViewerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var sharedPrefManager: SharedPrefManager
    private val resourcesRepository: ResourcesRepository = mockk(relaxed = true)
    private val ratingsRepository: RatingsRepository = mockk(relaxed = true)
    private val configurationsRepository: ConfigurationsRepository = mockk(relaxed = true)
    private lateinit var viewModel: ResourceViewerViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sharedPrefManager = SharedPrefManager(context, mockk(relaxed = true))
        viewModel = ResourceViewerViewModel(
            context = context,
            resourcesRepository = resourcesRepository,
            authSessionUpdaterFactory = mockk(relaxed = true),
            ratingsRepository = ratingsRepository,
            configurationsRepository = configurationsRepository,
            sharedPrefManager = sharedPrefManager,
            dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun calculateEffectivePlaybackPosition_nearEndThreshold() {
        // Less than or equal to 0 -> 0L
        assertEquals(0L, ResourceViewerViewModel.calculateEffectivePlaybackPosition(0L, 60000L))
        assertEquals(0L, ResourceViewerViewModel.calculateEffectivePlaybackPosition(-100L, 60000L))

        // Normal in-progress position -> currentPos
        assertEquals(30000L, ResourceViewerViewModel.calculateEffectivePlaybackPosition(30000L, 60000L))

        // Near end (within 2000ms threshold) -> resets to 0L
        assertEquals(0L, ResourceViewerViewModel.calculateEffectivePlaybackPosition(58500L, 60000L))
        assertEquals(0L, ResourceViewerViewModel.calculateEffectivePlaybackPosition(59999L, 60000L))
        assertEquals(0L, ResourceViewerViewModel.calculateEffectivePlaybackPosition(60000L, 60000L))

        // Exactly at or beyond threshold boundary
        assertEquals(58000L, ResourceViewerViewModel.calculateEffectivePlaybackPosition(58000L, 60000L))
    }

    @Test
    fun saveAndGetPlaybackProgress() {
        assertEquals(0L, viewModel.getPlaybackProgress("res123"))

        viewModel.savePlaybackProgress("res123", 45000L)
        assertEquals(45000L, viewModel.getPlaybackProgress("res123"))

        // Reset to 0 should remove key and return 0
        viewModel.savePlaybackProgress("res123", 0L)
        assertEquals(0L, viewModel.getPlaybackProgress("res123"))
    }

    @Test
    fun savePlaybackProgressWithDuration() {
        // In-progress save
        viewModel.savePlaybackProgress("video1", 25000L, 100000L)
        assertEquals(25000L, viewModel.getPlaybackProgress("video1"))

        // Near-end save resets to 0
        viewModel.savePlaybackProgress("video1", 99000L, 100000L)
        assertEquals(0L, viewModel.getPlaybackProgress("video1"))
    }

    @Test
    fun saveAndGetPlaybackSpeed() {
        assertEquals(1.0f, viewModel.getPlaybackSpeed(), 0.001f)

        viewModel.savePlaybackSpeed(1.5f)
        assertEquals(1.5f, viewModel.getPlaybackSpeed(), 0.001f)

        viewModel.savePlaybackSpeed(0.75f)
        assertEquals(0.75f, viewModel.getPlaybackSpeed(), 0.001f)
    }

    @Test
    fun `showRatingDialog returns false for already rated users`() = runTest {
        val userId = "123"
        val resourceId = "resourceId123"

        coEvery { ratingsRepository.isRatingPrompted(userId, resourceId) } returns false
        coEvery { ratingsRepository.getRatingSummary("resource", resourceId, userId) } returns
            RatingSummary(
                userRating = 4,
                existingRating = null,
                averageRating = 0f,
                totalRatings = 0
            )

        val result = viewModel.shouldShowResourceRatingDialog(userId, resourceId)

        assertFalse(result)
    }

    @Test
    fun `showRatingDialog returns true for never rated users`() = runTest {
        val userId = "123"
        val resourceId = "resourceId123"

        coEvery { ratingsRepository.isRatingPrompted(userId, resourceId) } returns false
        coEvery { ratingsRepository.getRatingSummary("resource", resourceId, userId) } returns
            RatingSummary(
                userRating = null,
                existingRating = null,
                averageRating = 0f,
                totalRatings = 0
            )

        val result = viewModel.shouldShowResourceRatingDialog(userId, resourceId)

        assertTrue(result)
    }

    @Test
    fun `showRatingDialog returns false for already prompted users`() = runTest {
        val userId = "123"
        val resourceId = "resourceId123"

        coEvery { ratingsRepository.isRatingPrompted(userId, resourceId) } returns true

        val result = viewModel.shouldShowResourceRatingDialog(userId, resourceId)

        assertFalse(result)
        coVerify(exactly = 0) { ratingsRepository.getRatingSummary(any(), any(), any()) }
    }
}
