package org.ole.planet.myplanet.ui.resources

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.repository.LocalResourceRequest
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class AddLocalResourceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher = mainDispatcherRule.testDispatcher
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private val resourcesRepository = mockk<ResourcesRepository>(relaxed = true)
    private lateinit var viewModel: AddLocalResourceViewModel

    @Before
    fun setup() {
        viewModel = AddLocalResourceViewModel(resourcesRepository)
    }

    @Test
    fun `checkTitle sets isTitleDuplicate to true when duplicate title exists`() = runTest {
        val title = "Duplicate Title"
        coEvery { resourcesRepository.resourceTitleExists(title) } returns true

        viewModel.checkTitle(title)
        advanceUntilIdle()

        assertTrue(viewModel.isTitleDuplicate.value)
    }

    @Test
    fun `checkTitle sets isTitleDuplicate to false when title is unique`() = runTest {
        val title = "Unique Title"
        coEvery { resourcesRepository.resourceTitleExists(title) } returns false

        viewModel.checkTitle(title)
        advanceUntilIdle()

        assertFalse(viewModel.isTitleDuplicate.value)
    }

    @Test
    fun `resetTitleCheck resets isTitleDuplicate to false`() = runTest {
        val title = "Duplicate Title"
        coEvery { resourcesRepository.resourceTitleExists(title) } returns true

        viewModel.checkTitle(title)
        advanceUntilIdle()
        assertTrue(viewModel.isTitleDuplicate.value)

        viewModel.resetTitleCheck()
        assertFalse(viewModel.isTitleDuplicate.value)
    }

    @Test
    fun `duplicate title blocks save or returns failure`() = runTest {
        val request = LocalResourceRequest(
            title = "Duplicate Title",
            addedBy = "User",
            author = "Author",
            year = "2023",
            description = "Desc",
            publisher = "Publisher",
            linkToLicense = "License",
            openWith = "",
            language = "",
            mediaType = "",
            resourceType = "",
            subjects = emptyList(),
            levels = emptyList(),
            resourceFor = emptyList(),
            resourceUrl = "file://path",
            userId = "user1",
            isPrivateTeamResource = false,
            teamId = null
        )
        coEvery { resourcesRepository.saveLocalResource(request) } returns Result.failure(Exception("Resource title already exists"))

        val result = viewModel.saveResource(request)

        assertTrue(result.isFailure)
        assertEquals("Resource title already exists", result.exceptionOrNull()?.message)
    }

    @Test
    fun `saveResource handles successful save`() = runTest {
        val request = LocalResourceRequest(
            title = "New Resource",
            addedBy = "User",
            author = "Author",
            year = "2023",
            description = "Desc",
            publisher = "Publisher",
            linkToLicense = "License",
            openWith = "",
            language = "",
            mediaType = "",
            resourceType = "",
            subjects = emptyList(),
            levels = emptyList(),
            resourceFor = emptyList(),
            resourceUrl = "file://path",
            userId = "user1",
            isPrivateTeamResource = false,
            teamId = null
        )
        coEvery { resourcesRepository.saveLocalResource(request) } returns Result.success(Unit)

        val result = viewModel.saveResource(request)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { resourcesRepository.saveLocalResource(request) }
    }

    @Test
    fun `updateResource handles successful update`() = runTest {
        val resourceId = "res123"
        val request = LocalResourceRequest(
            title = "Updated Title",
            addedBy = "User",
            author = "Updated Author",
            year = "2024",
            description = "Updated Desc",
            publisher = "Updated Publisher",
            linkToLicense = "Updated License",
            openWith = "",
            language = "",
            mediaType = "",
            resourceType = "",
            subjects = listOf("Math"),
            levels = listOf("Primary"),
            resourceFor = null,
            resourceUrl = null,
            userId = "user1",
            isPrivateTeamResource = false,
            teamId = null
        )
        coEvery {
            resourcesRepository.updateLocalResource(
                resourceId = resourceId,
                title = "Updated Title",
                author = "Updated Author",
                year = "2024",
                description = "Updated Desc",
                publisher = "Updated Publisher",
                linkToLicense = "Updated License",
                subjects = listOf("Math"),
                levels = listOf("Primary")
            )
        } returns Result.success(Unit)

        val result = viewModel.updateResource(resourceId, request)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `saveResource or updateResource handles failed UploadResult`() = runTest {
        val request = LocalResourceRequest(
            title = "Failed Resource",
            addedBy = "User",
            author = "Author",
            year = "2023",
            description = "Desc",
            publisher = "Publisher",
            linkToLicense = "License",
            openWith = "",
            language = "",
            mediaType = "",
            resourceType = "",
            subjects = emptyList(),
            levels = emptyList(),
            resourceFor = emptyList(),
            resourceUrl = "file://path",
            userId = "user1",
            isPrivateTeamResource = false,
            teamId = null
        )
        val uploadFailure = Exception("Failed to upload local resource")
        coEvery { resourcesRepository.saveLocalResource(request) } returns Result.failure(uploadFailure)

        val result = viewModel.saveResource(request)

        assertTrue(result.isFailure)
        assertEquals(uploadFailure, result.exceptionOrNull())
    }

    @Test
    fun `getResourceById returns resource from repository`() = runTest {
        val resourceId = "res123"
        val mockLibrary = MyLibrary().apply {
            id = resourceId
            title = "Sample Title"
        }
        coEvery { resourcesRepository.getResourceById(resourceId) } returns mockLibrary

        val result = viewModel.getResourceById(resourceId)

        assertEquals(mockLibrary, result)
    }
}
