package org.ole.planet.myplanet.ui.voices

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

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
    fun `getNewsWithReplies returns parent and replies in repository order`() = runTest {
        val parent = News().apply {
            _id = "p1"
            id = "p1"
        }
        val r1 = News().apply {
            _id = "r1"
            id = "r1"
            replyTo = "p1"
            time = 1L
        }
        val r2 = News().apply {
            _id = "r2"
            id = "r2"
            replyTo = "p1"
            time = 2L
        }
        val r3 = News().apply {
            _id = "r3"
            id = "r3"
            replyTo = "p1"
            time = 3L
        }

        val expectedReplies = listOf(r3, r2, r1)
        coEvery { voicesRepository.getNewsWithReplies("p1") } returns (parent to expectedReplies)

        val result = viewModel.getNewsWithReplies("p1")

        assertSame(parent, result.first)
        assertEquals(listOf("r3", "r2", "r1"), result.second.map { it._id })
        coVerify(exactly = 1) { voicesRepository.getNewsWithReplies("p1") }
    }

    @Test
    fun `getNewsWithReplies returns null parent when news is missing`() = runTest {
        coEvery { voicesRepository.getNewsWithReplies("missing") } returns (null to emptyList())

        val result = viewModel.getNewsWithReplies("missing")

        assertNull(result.first)
        assertEquals(emptyList<News>(), result.second)
        coVerify(exactly = 1) { voicesRepository.getNewsWithReplies("missing") }
    }

    @Test
    fun `getNewsWithReplies propagates repository exception`() = runTest {
        coEvery { voicesRepository.getNewsWithReplies(any()) } throws RuntimeException("boom")

        var thrownException: Exception? = null
        try {
            viewModel.getNewsWithReplies("p1")
        } catch (e: Exception) {
            thrownException = e
        }

        assertNotNull(thrownException)
        assertEquals("boom", thrownException?.message)
    }

    @Test
    fun `getNewsWithReplies passes newsId through unchanged`() = runTest {
        val targetId = "Abc-123"
        coEvery { voicesRepository.getNewsWithReplies(targetId) } returns (null to emptyList())

        viewModel.getNewsWithReplies(targetId)

        coVerify(exactly = 1) { voicesRepository.getNewsWithReplies(targetId) }
    }
}
