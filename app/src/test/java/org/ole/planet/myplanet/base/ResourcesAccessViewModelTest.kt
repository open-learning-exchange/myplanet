package org.ole.planet.myplanet.base

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ResourceUrlsResponse
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ResourcesAccessViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val resourcesRepository: ResourcesRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private lateinit var viewModel: ResourcesAccessViewModel

    @Before
    fun setUp() {
        viewModel = ResourcesAccessViewModel(
            resourcesRepository = resourcesRepository,
            userRepository = userRepository
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `resolveHtmlDownloadUrls returns DownloadNeeded when repository returns Success`() = runTest {
        val urls = listOf("http://example.com/file1.zip", "http://example.com/file2.zip")
        coEvery { resourcesRepository.getHtmlResourceDownloadUrls("html_res_1") } returns ResourceUrlsResponse.Success(urls)

        val outcome = viewModel.resolveHtmlDownloadUrls("html_res_1")

        assertTrue(outcome is HtmlOpenOutcome.DownloadNeeded)
        assertEquals(urls, (outcome as HtmlOpenOutcome.DownloadNeeded).urls)
    }

    @Test
    fun `resolveHtmlDownloadUrls returns ResourceNotFound when repository returns ResourceNotFound`() = runTest {
        coEvery { resourcesRepository.getHtmlResourceDownloadUrls("html_res_1") } returns ResourceUrlsResponse.ResourceNotFound

        val outcome = viewModel.resolveHtmlDownloadUrls("html_res_1")

        assertEquals(HtmlOpenOutcome.ResourceNotFound, outcome)
    }

    @Test
    fun `resolveHtmlDownloadUrls returns ResourceNotFound when resourceId is null`() = runTest {
        val outcome = viewModel.resolveHtmlDownloadUrls(null)

        assertEquals(HtmlOpenOutcome.ResourceNotFound, outcome)
        coVerify(exactly = 0) { resourcesRepository.getHtmlResourceDownloadUrls(any()) }
    }

    @Test
    fun `resolveHtmlDownloadUrls returns NoAttachments when repository returns NoAttachments`() = runTest {
        coEvery { resourcesRepository.getHtmlResourceDownloadUrls("html_res_1") } returns ResourceUrlsResponse.NoAttachments

        val outcome = viewModel.resolveHtmlDownloadUrls("html_res_1")

        assertEquals(HtmlOpenOutcome.NoAttachments, outcome)
    }

    @Test
    fun `resolveHtmlDownloadUrls returns Error when repository returns Error`() = runTest {
        coEvery { resourcesRepository.getHtmlResourceDownloadUrls("html_res_1") } returns ResourceUrlsResponse.Error

        val outcome = viewModel.resolveHtmlDownloadUrls("html_res_1")

        assertEquals(HtmlOpenOutcome.Error, outcome)
    }

    @Test
    fun `reconcileHtmlOffline calls repository reconcileHtmlResourceOffline`() = runTest {
        viewModel.reconcileHtmlOffline("html_res_1")

        coVerify(exactly = 1) { resourcesRepository.reconcileHtmlResourceOffline("html_res_1") }
    }

    @Test
    fun `trackOpen calls repository trackResourceOpen`() = runTest {
        val item = MyLibrary().apply { id = "lib_1"; resourceId = "res_1" }

        viewModel.trackOpen(item)

        coVerify(exactly = 1) { resourcesRepository.trackResourceOpen(item) }
    }

    @Test
    fun `findByLocalAddress returns matching items from repository`() = runTest {
        val localAddress = "path/to/file.mp4"
        val expectedList = listOf(MyLibrary().apply { id = "lib_1"; resourceLocalAddress = localAddress })
        coEvery { resourcesRepository.getLibraryItemsByLocalAddress(localAddress) } returns expectedList

        val result = viewModel.findByLocalAddress(localAddress)

        assertEquals(expectedList, result)
        coVerify(exactly = 1) { resourcesRepository.getLibraryItemsByLocalAddress(localAddress) }
    }

    @Test
    fun `isGuestUser returns true when user is guest`() = runTest {
        val guestUser = mockk<UserEntity>()
        coEvery { guestUser.isGuest() } returns true
        coEvery { userRepository.getUserModel() } returns guestUser

        val isGuest = viewModel.isGuestUser()

        assertTrue(isGuest)
    }

    @Test
    fun `isGuestUser returns false when user is not guest`() = runTest {
        val regularUser = mockk<UserEntity>()
        coEvery { regularUser.isGuest() } returns false
        coEvery { userRepository.getUserModel() } returns regularUser

        val isGuest = viewModel.isGuestUser()

        assertFalse(isGuest)
    }

    @Test
    fun `isGuestUser returns true when userModel is null`() = runTest {
        coEvery { userRepository.getUserModel() } returns null

        val isGuest = viewModel.isGuestUser()

        assertTrue(isGuest)
    }
}
