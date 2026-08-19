package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun addReply_marksFeedbackPendingSoItGetsUploaded() = runTest {
        val feedback = Feedback().apply {
            id = "fb1"
            isUploaded = true
            messages = "[]"
        }
        coEvery { feedbackDao.findById("fb1") } returns feedback
        val updated = slot<Feedback>()
        coEvery { feedbackDao.update(capture(updated)) } returns Unit

        repository.addReply("fb1", "a reply", "someUser")

        assertFalse(updated.captured.isUploaded)
        val messages = Gson().fromJson(updated.captured.messages, JsonArray::class.java)
        assertEquals(1, messages.size())
        assertEquals("a reply", messages[0].asJsonObject["message"].asString)
    }

    @Test
    fun insertFromJson_withPendingLocalReply_preservesLocalMessagesAndPendingState() = runTest {
        val existing = Feedback().apply {
            id = "fb1"
            isUploaded = false
            messages = "[{\"message\":\"local unsynced reply\"}]"
        }
        coEvery { feedbackDao.findById("fb1") } returns existing

        val serverDoc = JsonObject().apply {
            addProperty("_id", "fb1")
            addProperty("status", "Closed")
            add("messages", JsonArray().apply { add(JsonObject().apply { addProperty("message", "server reply") } ) })
        }

        val saved = slot<Feedback>()
        coEvery { feedbackDao.upsert(capture(saved)) } returns Unit

        repository.insertFromJson(serverDoc)

        assertEquals("Closed", saved.captured.status)
        assertFalse(saved.captured.isUploaded)
        assertEquals(existing.messages, saved.captured.messages)
    }

    @Test
    fun insertFromJson_withNoPendingLocalChanges_overwritesFromServer() = runTest {
        coEvery { feedbackDao.findById("fb1") } returns null

        val serverDoc = JsonObject().apply {
            addProperty("_id", "fb1")
            addProperty("status", "Open")
            add("messages", JsonArray().apply { add(JsonObject().apply { addProperty("message", "server reply") } ) })
        }

        val saved = slot<Feedback>()
        coEvery { feedbackDao.upsert(capture(saved)) } returns Unit

        repository.insertFromJson(serverDoc)

        assertTrue(saved.captured.isUploaded)
        val messages = Gson().fromJson(saved.captured.messages, JsonArray::class.java)
        assertEquals("server reply", messages[0].asJsonObject["message"].asString)
    }

    @Test
    fun `getFeedback deduplicates byte-identical flow emissions for manager`() = runTest {
        val user = mockk<org.ole.planet.myplanet.model.UserEntity> {
            every { isManager() } returns true
        }
        val f1 = Feedback().apply { id = "f1"; _rev = "rev1"; status = "Open"; isUploaded = true; messages = "[]" }
        val f2 = Feedback().apply { id = "f1"; _rev = "rev1"; status = "Open"; isUploaded = true; messages = "[]" }
        coEvery { feedbackDao.getAllSortedFlow() } returns flowOf(listOf(f1), listOf(f2))

        val emissions = mutableListOf<List<Feedback>>()
        repository.getFeedback(user).collect { emissions.add(it) }

        assertEquals(1, emissions.size)
    }

    @Test
    fun `getFeedback deduplicates byte-identical flow emissions for owner`() = runTest {
        val user = mockk<org.ole.planet.myplanet.model.UserEntity> {
            every { isManager() } returns false
            every { name } returns "ownerName"
        }
        val f1 = Feedback().apply { id = "f1"; _rev = "rev1"; status = "Open"; isUploaded = true; messages = "[]" }
        val f2 = Feedback().apply { id = "f1"; _rev = "rev1"; status = "Open"; isUploaded = true; messages = "[]" }
        coEvery { feedbackDao.getByOwnerFlow("ownerName") } returns flowOf(listOf(f1), listOf(f2))

        val emissions = mutableListOf<List<Feedback>>()
        repository.getFeedback(user).collect { emissions.add(it) }

        assertEquals(1, emissions.size)
    }
}
