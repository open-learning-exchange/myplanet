package org.ole.planet.myplanet.ui.user

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ProfileActivityStats
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var userRepository: UserRepository
    private lateinit var activitiesRepository: ActivitiesRepository
    private lateinit var viewModel: UserProfileViewModel

    private val dispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val mainImmediate: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    @Before
    fun setup() {
        userRepository = mockk(relaxed = true)
        activitiesRepository = mockk(relaxed = true)

        val mockUser = mockk<UserEntity>(relaxed = true)
        every { mockUser.name } returns "Test User"
        coEvery { userRepository.getUserModel() } returns mockUser

        coEvery { activitiesRepository.getProfileActivityStats("Test User") } returns ProfileActivityStats(
            mostOpenedResource = Pair("Test Resource", 5),
            lastVisit = 123456789L,
            resourceOpenCount = 10L
        )

        viewModel = UserProfileViewModel(userRepository, activitiesRepository)
    }

    @Test
    fun `init loads profile activity stats into state flows`() = runTest {
        advanceUntilIdle()

        assertEquals("Test Resource opened 5 times", viewModel.maxOpenedResource.value)
        assertEquals(123456789L, viewModel.lastVisit.value)
        assertEquals("Resource opened 10 times.", viewModel.numberOfResourceOpen.value)
    }

    @Test
    fun `updateCurrentUserProfile with blank active userId sets updateState to Error without invoking userRepository`() = runTest {
        coEvery { userRepository.getActiveUserIdSuspending() } returns ""

        viewModel.updateCurrentUserProfile(
            firstName = "John",
            lastName = "Doe",
            middleName = null,
            email = "john@example.com",
            phoneNumber = "1234567890",
            level = null,
            language = null,
            gender = null,
            dob = null
        )

        advanceUntilIdle()

        assertEquals(ProfileUpdateState.Error("Invalid user id"), viewModel.updateState.value)
        coVerify(exactly = 0) { userRepository.updateUserDetails(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `updateCurrentUserProfile success sets updateState to Success and updates userModel`() = runTest {
        val userId = "user123"
        coEvery { userRepository.getActiveUserIdSuspending() } returns userId

        val mockUser = mockk<UserEntity>()
        coEvery { userRepository.updateUserDetails(
            userId = userId,
            firstName = "John",
            lastName = "Doe",
            middleName = null,
            email = "john@example.com",
            phoneNumber = "1234567890",
            level = null,
            language = null,
            gender = null,
            dob = null
        ) } returns mockUser

        viewModel.updateCurrentUserProfile(
            firstName = "John",
            lastName = "Doe",
            middleName = null,
            email = "john@example.com",
            phoneNumber = "1234567890",
            level = null,
            language = null,
            gender = null,
            dob = null
        )

        advanceUntilIdle()

        assertEquals(ProfileUpdateState.Success, viewModel.updateState.value)
        assertEquals(mockUser, viewModel.userModel.value)
    }

    @Test
    fun `updateCurrentUserProfile exception sets updateState to Error with exception message`() = runTest {
        val userId = "user123"
        coEvery { userRepository.getActiveUserIdSuspending() } returns userId
        val errorMessage = "Database error"
        coEvery { userRepository.updateUserDetails(
            userId = userId,
            firstName = "John",
            lastName = "Doe",
            middleName = null,
            email = "john@example.com",
            phoneNumber = "1234567890",
            level = null,
            language = null,
            gender = null,
            dob = null
        ) } throws Exception(errorMessage)

        viewModel.updateCurrentUserProfile(
            firstName = "John",
            lastName = "Doe",
            middleName = null,
            email = "john@example.com",
            phoneNumber = "1234567890",
            level = null,
            language = null,
            gender = null,
            dob = null
        )

        advanceUntilIdle()

        assertEquals(ProfileUpdateState.Error(errorMessage), viewModel.updateState.value)
    }

    @Test
    fun `loadCurrentUserProfile sets userModel to value returned by userRepository`() = runTest {
        val userId = "user123"
        coEvery { userRepository.getActiveUserIdSuspending() } returns userId
        val mockUser = mockk<UserEntity>()
        coEvery { userRepository.getUserByAnyId(userId) } returns mockUser

        viewModel.loadCurrentUserProfile()

        advanceUntilIdle()

        assertEquals(mockUser, viewModel.userModel.value)
        coVerify(exactly = 1) { userRepository.getUserByAnyId(userId) }
    }
}
