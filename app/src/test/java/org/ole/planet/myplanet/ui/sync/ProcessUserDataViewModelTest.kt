package org.ole.planet.myplanet.ui.sync

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.repository.SyncRepository
import org.ole.planet.myplanet.repository.SyncUiState
import org.ole.planet.myplanet.repository.UserRepository

class ProcessUserDataViewModelTest {

    private val syncRepository: SyncRepository = mockk()
    private val userRepository: UserRepository = mockk(relaxed = true)

    private fun createViewModel(): ProcessUserDataViewModel {
        return ProcessUserDataViewModel(syncRepository, userRepository)
    }

    @Test
    fun `uploadLoginData returns repository flow untransformed including terminal emission`() = runTest {
        val expectedStates = listOf(
            SyncUiState.Loading,
            SyncUiState.Success("Upload successful")
        )
        every { syncRepository.uploadLoginData() } returns flowOf(*expectedStates.toTypedArray())

        val viewModel = createViewModel()
        val result = viewModel.uploadLoginData().toList()

        assertEquals(expectedStates, result)
        verify(exactly = 1) { syncRepository.uploadLoginData() }
    }

    @Test
    fun `uploadBulkData returns repository flow untransformed including terminal emission`() = runTest {
        val expectedStates = listOf(
            SyncUiState.Loading,
            SyncUiState.Error("Upload failed")
        )
        every { syncRepository.uploadBulkData() } returns flowOf(*expectedStates.toTypedArray())

        val viewModel = createViewModel()
        val result = viewModel.uploadBulkData().toList()

        assertEquals(expectedStates, result)
        verify(exactly = 1) { syncRepository.uploadBulkData() }
    }

    @Test
    fun `fetchUserSecurityData calls fetchUserSecurityData on userRepository`() = runTest {
        val userName = "test_user"
        val viewModel = createViewModel()

        viewModel.fetchUserSecurityData(userName)

        coVerify(exactly = 1) { userRepository.fetchUserSecurityData(userName) }
    }
}
