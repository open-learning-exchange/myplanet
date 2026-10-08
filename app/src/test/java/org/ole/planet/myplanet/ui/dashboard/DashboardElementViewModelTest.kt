package org.ole.planet.myplanet.ui.dashboard

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.UserSessionManager

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardElementViewModelTest {

    private val userRepository = mockk<UserRepository>()
    private val activitiesRepository = mockk<ActivitiesRepository>()
    private val userSessionManager = mockk<UserSessionManager>()

    private lateinit var viewModel: DashboardElementViewModel

    @Before
    fun setUp() {
        viewModel = DashboardElementViewModel(
            userRepository,
            activitiesRepository,
            userSessionManager
        )
    }

    @Test
    fun testCurrentUser_delegatesToUserRepository() = runTest {
        val expectedUser = UserEntity().apply { id = "user_123" }
        coEvery { userRepository.getUserModel() } returns expectedUser

        val actualUser = viewModel.currentUser()

        assertEquals(expectedUser, actualUser)
        coVerify(exactly = 1) { userRepository.getUserModel() }
    }

    @Test
    fun testRecordUserChallengeAction_delegatesToActivitiesRepository() = runTest {
        val userId = "user_123"
        coEvery { activitiesRepository.recordSyncUserChallengeAction(userId) } returns Unit

        viewModel.recordUserChallengeAction(userId)

        coVerify(exactly = 1) { activitiesRepository.recordSyncUserChallengeAction(userId) }
    }

    @Test
    fun testLogout_delegatesToUserSessionManager() = runTest {
        coEvery { userSessionManager.logoutAsync() } returns Unit

        viewModel.logout()

        coVerify(exactly = 1) { userSessionManager.logoutAsync() }
    }
}
