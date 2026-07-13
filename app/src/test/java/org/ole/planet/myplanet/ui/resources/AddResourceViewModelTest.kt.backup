package org.ole.planet.myplanet.ui.resources

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.repository.PersonalsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AddResourceViewModelTest {

    private lateinit var viewModel: AddResourceViewModel
    private val personalsRepository = mockk<PersonalsRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AddResourceViewModel(personalsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `checkTitleExists updates state to TitleExists when title exists`() = runTest {
        val title = "ExistingTitle"
        val userId = "123"
        coEvery { personalsRepository.personalTitleExists(title, userId) } returns true

        viewModel.checkTitleExists(title, userId)
        advanceUntilIdle()

        val state = viewModel.state.first()
        assertEquals(AddResourceState.TitleExists, state)
    }

    @Test
    fun `checkTitleExists does not update state when title does not exist`() = runTest {
        val title = "NewTitle"
        val userId = "123"
        coEvery { personalsRepository.personalTitleExists(title, userId) } returns false

        viewModel.checkTitleExists(title, userId)
        advanceUntilIdle()

        val state = viewModel.state.first()
        assertEquals(AddResourceState.Idle, state)
    }

    @Test
    fun `saveResource updates state to TitleExists when title exists`() = runTest {
        val title = "ExistingTitle"
        val userId = "123"
        coEvery { personalsRepository.personalTitleExists(title, userId) } returns true

        viewModel.saveResource(title, userId, "User Name", "path/to/file", "desc")
        advanceUntilIdle()

        val state = viewModel.state.first()
        assertEquals(AddResourceState.TitleExists, state)
        coVerify(exactly = 0) { personalsRepository.savePersonalResource(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `saveResource saves resource and updates state to Success when title does not exist`() = runTest {
        val title = "NewTitle"
        val userId = "123"
        val userName = "User Name"
        val path = "path/to/file"
        val desc = "desc"

        coEvery { personalsRepository.personalTitleExists(title, userId) } returns false

        viewModel.saveResource(title, userId, userName, path, desc)
        advanceUntilIdle()

        val state = viewModel.state.first()
        assertEquals(AddResourceState.Success, state)
        coVerify(exactly = 1) { personalsRepository.savePersonalResource(title, userId, userName, path, desc) }
    }

    @Test
    fun `resetState updates state to Idle`() = runTest {
        viewModel.resetState()
        val state = viewModel.state.first()
        assertEquals(AddResourceState.Idle, state)
    }
}
