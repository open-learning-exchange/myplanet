package org.ole.planet.myplanet.ui.settings

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.RetryRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = object : DispatcherProvider {
        override val main = testDispatcher
        override val mainImmediate = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
        override val unconfined = testDispatcher
    }

    private val configurationsRepository: ConfigurationsRepository = mockk(relaxed = true)
    private val retryRepository: RetryRepository = mockk(relaxed = true)
    private val resourcesRepository: ResourcesRepository = mockk(relaxed = true)
    private val userSessionManager: UserSessionManager = mockk(relaxed = true)

    @Test
    fun `clearAllData calls clearLocalAppData on configurationsRepository and emits clearDataEvent`() = runTest {
        coEvery { configurationsRepository.clearLocalAppData() } just runs

        val viewModel = SettingsViewModel(
            configurationsRepository,
            retryRepository,
            resourcesRepository,
            dispatcherProvider,
            userSessionManager
        )

        viewModel.clearAllData()
        advanceUntilIdle()

        val event = viewModel.clearDataEvent.first()
        assertNotNull(event)

        coVerify { configurationsRepository.clearLocalAppData() }
    }

    @Test
    fun `isGuest is true for id guest_abc`() = runTest {
        coEvery { userSessionManager.getUserModel() } returns UserEntity(id = "guest_abc")

        val viewModel = SettingsViewModel(
            configurationsRepository,
            retryRepository,
            resourcesRepository,
            dispatcherProvider,
            userSessionManager
        )

        assertTrue(viewModel.isGuest())
    }

    @Test
    fun `isGuest is true for id guest`() = runTest {
        coEvery { userSessionManager.getUserModel() } returns UserEntity(id = "guest")

        val viewModel = SettingsViewModel(
            configurationsRepository,
            retryRepository,
            resourcesRepository,
            dispatcherProvider,
            userSessionManager
        )

        assertTrue(viewModel.isGuest())
    }

    @Test
    fun `isGuest is false for a normal id`() = runTest {
        coEvery { userSessionManager.getUserModel() } returns UserEntity(id = "user123")

        val viewModel = SettingsViewModel(
            configurationsRepository,
            retryRepository,
            resourcesRepository,
            dispatcherProvider,
            userSessionManager
        )

        assertFalse(viewModel.isGuest())
    }

    @Test
    fun `isGuest is false when getUserModel() returns null`() = runTest {
        coEvery { userSessionManager.getUserModel() } returns null

        val viewModel = SettingsViewModel(
            configurationsRepository,
            retryRepository,
            resourcesRepository,
            dispatcherProvider,
            userSessionManager
        )

        assertFalse(viewModel.isGuest())
    }

    @Test
    fun `isGuest is false when the user's id is null`() = runTest {
        val userWithNullId = UserEntity()
        UserEntity::class.java.getField("id").set(userWithNullId, null)
        coEvery { userSessionManager.getUserModel() } returns userWithNullId

        val viewModel = SettingsViewModel(
            configurationsRepository,
            retryRepository,
            resourcesRepository,
            dispatcherProvider,
            userSessionManager
        )

        assertFalse(viewModel.isGuest())
    }
}
