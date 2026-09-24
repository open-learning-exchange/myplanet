package org.ole.planet.myplanet.ui.resources

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.RatingSummary
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ResourceDetailViewModelTest {

    private val userRepository = mockk<UserRepository>()
    private val resourcesRepository = mockk<ResourcesRepository>()
    private val ratingsRepository = mockk<RatingsRepository>()
    private lateinit var viewModel: ResourceDetailViewModel

    @Before
    fun setup() {
        viewModel = ResourceDetailViewModel(
            userRepository = userRepository,
            resourcesRepository = resourcesRepository,
            ratingsRepository = ratingsRepository
        )
    }

    @Test
    fun `getUserModel returns user from userRepository`() = runTest {
        val user = UserEntity(id = "user1", name = "Test User")
        coEvery { userRepository.getUserModel() } returns user

        val result = viewModel.getUserModel()

        assertEquals(user, result)
        coVerify(exactly = 1) { userRepository.getUserModel() }
    }

    @Test
    fun `getUserModel propagates exception when userRepository throws`() = runTest {
        val exception = RuntimeException("User fetch error")
        coEvery { userRepository.getUserModel() } throws exception

        try {
            viewModel.getUserModel()
            assert(false) { "Expected exception was not thrown" }
        } catch (e: Exception) {
            assertEquals(exception, e)
        }
    }

    @Test
    fun `resolveLibraryItem forwards id and returns library from resourcesRepository`() = runTest {
        val library = MyLibrary(_id = "lib123", title = "Test Library")
        coEvery { resourcesRepository.resolveLibraryItem("lib123") } returns library

        val result = viewModel.resolveLibraryItem("lib123")

        assertEquals(library, result)
        coVerify(exactly = 1) { resourcesRepository.resolveLibraryItem("lib123") }
    }

    @Test
    fun `resolveLibraryItem propagates exception when resourcesRepository throws`() = runTest {
        val exception = RuntimeException("Resolve error")
        coEvery { resourcesRepository.resolveLibraryItem("lib123") } throws exception

        try {
            viewModel.resolveLibraryItem("lib123")
            assert(false) { "Expected exception was not thrown" }
        } catch (e: Exception) {
            assertEquals(exception, e)
        }
    }

    @Test
    fun `setUserLibrary forwards parameters and returns library from resourcesRepository`() = runTest {
        val library = MyLibrary(_id = "lib123", title = "Test Library")
        coEvery { resourcesRepository.setUserLibrary("lib123", true) } returns library

        val result = viewModel.setUserLibrary("lib123", true)

        assertEquals(library, result)
        coVerify(exactly = 1) { resourcesRepository.setUserLibrary("lib123", true) }
    }

    @Test
    fun `setUserLibrary propagates exception when resourcesRepository throws`() = runTest {
        val exception = RuntimeException("Set user library error")
        coEvery { resourcesRepository.setUserLibrary("lib123", false) } throws exception

        try {
            viewModel.setUserLibrary("lib123", false)
            assert(false) { "Expected exception was not thrown" }
        } catch (e: Exception) {
            assertEquals(exception, e)
        }
    }

    @Test
    fun `getRatingSummary forwards parameters and returns summary from ratingsRepository`() = runTest {
        val summary = RatingSummary(average = 4.5f, total = 10)
        coEvery { ratingsRepository.getRatingSummary("resource", "res123", "user123") } returns summary

        val result = viewModel.getRatingSummary("resource", "res123", "user123")

        assertEquals(summary, result)
        coVerify(exactly = 1) { ratingsRepository.getRatingSummary("resource", "res123", "user123") }
    }

    @Test
    fun `getRatingSummary propagates exception when ratingsRepository throws`() = runTest {
        val exception = RuntimeException("Rating summary error")
        coEvery { ratingsRepository.getRatingSummary("resource", "res123", null) } throws exception

        try {
            viewModel.getRatingSummary("resource", "res123", null)
            assert(false) { "Expected exception was not thrown" }
        } catch (e: Exception) {
            assertEquals(exception, e)
        }
    }
}
