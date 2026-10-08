package org.ole.planet.myplanet.ui.voices

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

/**
 * [ReplyViewModel.getNewsWithReplies] is a pure pass-through; ordering and the missing-parent case
 * are covered against real Room in `VoicesRepositoryNewsWithRepliesTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var voicesRepository: VoicesRepository
    private lateinit var viewModel: ReplyViewModel

    @Before
    fun setup() {
        voicesRepository = mockk()
        viewModel = ReplyViewModel(voicesRepository)
    }

    @Test
    fun `getNewsWithReplies returns the repository result for the given id`() = runTest {
        val expected = News().apply { id = "p1" } to listOf(News().apply { id = "r1" })
        coEvery { voicesRepository.getNewsWithReplies("p1") } returns expected

        assertSame(expected, viewModel.getNewsWithReplies("p1"))
    }

    @Test(expected = IllegalStateException::class)
    fun `getNewsWithReplies propagates repository exception`() = runTest {
        coEvery { voicesRepository.getNewsWithReplies(any()) } throws IllegalStateException("boom")

        viewModel.getNewsWithReplies("p1")
    }
}
