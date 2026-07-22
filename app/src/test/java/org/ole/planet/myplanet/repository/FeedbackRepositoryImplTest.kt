package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.FeedbackDao
import org.ole.planet.myplanet.model.Feedback

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackRepositoryImplTest {

    private lateinit var feedbackDao: FeedbackDao
    private lateinit var repository: FeedbackRepositoryImpl

    @Before
    fun setup() {
        feedbackDao = mockk(relaxed = true)
        repository = FeedbackRepositoryImpl(feedbackDao, Gson())
    }

    @Test
    fun getPendingFeedback_returnsOnlyUnuploadedFeedback() = runTest {
        val feedback1 = Feedback().apply { isUploaded = false }
        val feedback2 = Feedback().apply { isUploaded = false }
        coEvery { feedbackDao.getPending() } returns listOf(feedback1, feedback2)

        val result = repository.getPendingFeedback()

        coVerify { feedbackDao.getPending() }
        assertEquals(2, result.size)
    }
}
