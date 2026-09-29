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

@OptIn(ExperimentalCoroutinesApi::class)
class AddLocalResourceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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
    fun `saveResource handles failure result when duplicate title exists`() = runTest {
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
    fun `updateResource maps request fields and handles successful update`() = runTest {
        val resourceId = "res123"
        val subjectsList = listOf("Math", "Science")
        val levelsList = listOf("Primary", "Secondary")
        val request = LocalResourceRequest(
            title = "Updated Title",
            addedBy = null,
            author = "Updated Author",
            year = "2024",
            description = "Updated Desc",
            publisher = "Updated Publisher",
            linkToLicense = "Updated License",
            openWith = null,
            language = null,
            mediaType = null,
            resourceType = null,
            subjects = subjectsList,
            levels = levelsList,
            resourceFor = null,
            resourceUrl = null,
            userId = null,
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
                subjects = subjectsList,
                levels = levelsList
            )
        } returns Result.success(Unit)

        val result = viewModel.updateResource(resourceId, request)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            resourcesRepository.updateLocalResource(
                resourceId = resourceId,
                title = "Updated Title",
                author = "Updated Author",
                year = "2024",
                description = "Updated Desc",
                publisher = "Updated Publisher",
                linkToLicense = "Updated License",
                subjects = subjectsList,
                levels = levelsList
            )
        }
    }

    @Test
    fun `updateResource handles failure result`() = runTest {
        val resourceId = "res123"
        val request = LocalResourceRequest(
            title = "Updated Title",
            addedBy = null,
            author = "Updated Author",
            year = "2024",
            description = "Updated Desc",
            publisher = "Updated Publisher",
            linkToLicense = "Updated License",
            openWith = null,
            language = null,
            mediaType = null,
            resourceType = null,
            subjects = listOf("Math"),
            levels = listOf("Primary"),
            resourceFor = null,
            resourceUrl = null,
            userId = null,
            isPrivateTeamResource = false,
            teamId = null
        )
        val updateFailure = Exception("Failed to update resource")
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
        } returns Result.failure(updateFailure)

        val result = viewModel.updateResource(resourceId, request)

        assertTrue(result.isFailure)
        assertEquals(updateFailure, result.exceptionOrNull())
    }

    @Test
    fun `saveResource handles failed upload or save result`() = runTest {
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
