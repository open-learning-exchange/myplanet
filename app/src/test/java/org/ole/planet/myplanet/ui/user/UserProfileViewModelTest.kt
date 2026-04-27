package org.ole.planet.myplanet.ui.user

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var userRepository: UserRepository
    private lateinit var userSessionManager: UserSessionManager
    private lateinit var activitiesRepository: ActivitiesRepository
    private lateinit var viewModel: UserProfileViewModel

    private val dispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    @Before
    fun setup() {
        userRepository = mockk(relaxed = true)
        userSessionManager = mockk(relaxed = true)
        activitiesRepository = mockk(relaxed = true)

        val mockUser = mockk<RealmUser>(relaxed = true)
        every { mockUser.name } returns "Test User"
        coEvery { userSessionManager.getUserModel() } returns mockUser

        coEvery { activitiesRepository.getMostOpenedResource("Test User", UserSessionManager.KEY_RESOURCE_OPEN) } returns Pair("Test Resource", 5)
        coEvery { activitiesRepository.getGlobalLastVisit() } returns 123456789L
        coEvery { activitiesRepository.getResourceOpenCount("Test User", UserSessionManager.KEY_RESOURCE_OPEN) } returns 10L

        viewModel = UserProfileViewModel(userRepository, userSessionManager, activitiesRepository, dispatcherProvider)
    }

    @Test
    fun `updateUserProfile with null userId sets updateState to Error without invoking userRepository`() = runTest {
        viewModel.updateUserProfile(
            userId = null,
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
    fun `updateUserProfile success sets updateState to Success and updates userModel`() = runTest {
        val userId = "user123"
        val mockUser = mockk<RealmUser>()
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

        viewModel.updateUserProfile(
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
        )

        advanceUntilIdle()

        assertEquals(ProfileUpdateState.Success, viewModel.updateState.value)
        assertEquals(mockUser, viewModel.userModel.value)
    }

    @Test
    fun `updateUserProfile exception sets updateState to Error with exception message`() = runTest {
        val userId = "user123"
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

        viewModel.updateUserProfile(
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
        )

        advanceUntilIdle()

        assertEquals(ProfileUpdateState.Error(errorMessage), viewModel.updateState.value)
    }

    @Test
    fun `loadUserProfile sets userModel to value returned by userRepository`() = runTest {
        val userId = "user123"
        val mockUser = mockk<RealmUser>()
        coEvery { userRepository.getUserByAnyId(userId) } returns mockUser

        viewModel.loadUserProfile(userId)

        advanceUntilIdle()

        assertEquals(mockUser, viewModel.userModel.value)
        coVerify(exactly = 1) { userRepository.getUserByAnyId(userId) }
    }
}
