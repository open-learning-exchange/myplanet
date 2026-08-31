package org.ole.planet.myplanet.ui.viewer

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.RatingSummary
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
internal class ResourceViewerViewModelTest {
    private val resourcesRepository = mockk<ResourcesRepository>(relaxed = true)
    private val sharedPrefManager = mockk<SharedPrefManager>(relaxed = true)
    private val ratingsRepository = mockk<RatingsRepository>(relaxed = true)
    private val configurationsRepository = mockk<ConfigurationsRepository>(relaxed = true)
    private lateinit var viewModel: ResourceViewerViewModel
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setup() {
        viewModel = ResourceViewerViewModel(
            context = mockk(relaxed = true),
            resourcesRepository = resourcesRepository,
            authSessionUpdaterFactory = mockk(relaxed = true),
            ratingsRepository = ratingsRepository,
            configurationsRepository = configurationsRepository,
            dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `showRatingDialog returns false for already rated users` () = runTest {
        val userId = "123"
        val resourceId = "resourceId123"

        every {
            sharedPrefManager.getRawString(
                "rating_prompted_${userId}_${resourceId}",
                "false"
            )
        } returns "false"
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
    fun `showRatingDialog returns true for never rated users` () = runTest {
        val userId = "123"
        val resourceId = "resourceId123"

        every {
            sharedPrefManager.getRawString(
                "rating_prompted_${userId}_${resourceId}",
                "false"
            )
        } returns "false"
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
    fun `showRatingDialog return false for already prompted users` () = runTest {
        val userId = "123"
        val resourceId = "resourceId123"

        every {
            sharedPrefManager.getRawString(
                "rating_prompted_${userId}_${resourceId}",
                "false"
            )
        } returns "true"

        val result = viewModel.shouldShowResourceRatingDialog(userId, resourceId)

        assertFalse(result)
    }
}
