package org.ole.planet.myplanet.ui.viewer

import androidx.annotation.OptIn
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import junit.framework.TestCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.RatingSummary
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
internal class ResourceViewerViewModelTest {
    private val resourcesRepository = mockk<ResourcesRepository>(relaxed = true)
    private val sharedPrefManager = mockk<SharedPrefManager>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val ratingsRepository = mockk<RatingsRepository>(relaxed = true)
    private lateinit var viewModel: ResourceViewerViewModel
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setup() {
        viewModel = ResourceViewerViewModel(
            resourcesRepository = resourcesRepository,
            authSessionUpdaterFactory = mockk(relaxed = true),
            serverUrlMapper = mockk(relaxed = true),
            sharedPrefManager = sharedPrefManager,
            userRepository = userRepository,
            ratingsRepository = ratingsRepository
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

        val mockUser = UserEntity().apply { id = userId }
        coEvery { userRepository.getUserModel() } returns mockUser
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

        val result = viewModel.shouldShowResourceRatingDialog(resourceId)

        TestCase.assertFalse(result)
    }

    @Test
    fun `showRatingDialog returns true for never rated users` () = runTest {
        val userId = "123"
        val resourceId = "resourceId123"

        val mockUser = UserEntity().apply { id = userId }
        coEvery { userRepository.getUserModel() } returns mockUser
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

        val result = viewModel.shouldShowResourceRatingDialog(resourceId)

        TestCase.assertTrue(result)
    }

    @Test
    fun `showRatingDialog return false for already prompted users` () = runTest {
        val userId = "123"
        val resourceId = "resourceId123"

        val mockUser = UserEntity().apply { id = userId }
        coEvery { userRepository.getUserModel() } returns mockUser
        every {
            sharedPrefManager.getRawString(
                "rating_prompted_${userId}_${resourceId}",
                "false"
            )
        } returns "true"

        val result = viewModel.shouldShowResourceRatingDialog(resourceId)

        TestCase.assertFalse(result)
    }
}
