package org.ole.planet.myplanet.repository

import android.app.Application
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.data.room.dao.AnswerDao
import org.ole.planet.myplanet.data.room.dao.SubmissionDao
import org.ole.planet.myplanet.model.Answer
import org.ole.planet.myplanet.model.Submission
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real in-memory Room coverage for [SubmissionsRepositoryImpl.insertSubmission]'s sync upsert. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SubmissionsRepositorySyncTest {

    private lateinit var db: AppDatabase
    private lateinit var submissionDao: SubmissionDao
    private lateinit var answerDao: AnswerDao
    private lateinit var repository: SubmissionsRepositoryImpl

    private fun pulledSubmission(remoteId: String, status: String = "complete") = JsonObject().apply {
        addProperty("_id", remoteId)
        addProperty("_rev", "2-abc")
        addProperty("parentId", "exam1@course1")
        addProperty("type", "exam")
        addProperty("status", status)
        add("user", JsonObject().apply { addProperty("_id", "org.couchdb.user:learner") })
        add("answers", JsonArray().apply {
            add(JsonObject().apply { addProperty("value", "A"); addProperty("questionId", "q1") })
            add(JsonObject().apply { addProperty("value", "B"); addProperty("questionId", "q2") })
        })
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        submissionDao = db.submissionDao()
        answerDao = db.answerDao()
        repository = SubmissionsRepositoryImpl(
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            submissionDao,
            answerDao,
            mockk(relaxed = true),
            mockk(relaxed = true),
            Gson(),
            mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun storeUploadedLocalSubmission() {
        submissionDao.upsertAll(listOf(Submission(id = "local-uuid", parentId = "exam1@course1", type = "exam", status = "complete")))
        submissionDao.markUploaded("local-uuid", "server-1", "1-abc")
        answerDao.upsertAll(listOf(
            Answer(id = "ans-a", value = "A", questionId = "q1", submissionId = "local-uuid"),
            Answer(id = "ans-b", value = "B", questionId = "q2", submissionId = "local-uuid"),
        ))
    }

    @Test
    fun `pulling an uploaded local submission updates it instead of adding a second row`() = runBlocking {
        storeUploadedLocalSubmission()

        repository.insertSubmission(pulledSubmission("server-1"))

        val rows = submissionDao.getByRemoteIds(listOf("server-1"))
        assertEquals(listOf("local-uuid"), rows.map { it.id })
        assertEquals("2-abc", rows[0]._rev)
        assertEquals(listOf("ans-a", "ans-b"), answerDao.getBySubmissionId("local-uuid").map { it.id }.sorted())
        assertEquals(emptyList<Answer>(), answerDao.getBySubmissionId("server-1"))
    }

    @Test
    fun `pulling removes a duplicate and its answers already stored under the server id`() = runBlocking {
        storeUploadedLocalSubmission()
        submissionDao.upsertAll(listOf(Submission(id = "server-1", _id = "server-1", parentId = "exam1@course1", type = "exam")))
        answerDao.upsertAll(listOf(Answer(id = "server-1-0", questionId = "q1", submissionId = "server-1")))

        repository.insertSubmission(pulledSubmission("server-1"))

        assertEquals(listOf("local-uuid"), submissionDao.getByRemoteIds(listOf("server-1")).map { it.id })
        assertEquals(emptyList<Answer>(), answerDao.getBySubmissionId("server-1"))
        assertEquals(2, answerDao.getBySubmissionId("local-uuid").size)
    }

    @Test
    fun `submissions created on the server are stored under their server id as before`() = runBlocking {
        repository.insertSubmission(pulledSubmission("server-2"))

        assertEquals(listOf("server-2"), submissionDao.getByRemoteIds(listOf("server-2")).map { it.id })
        assertEquals(listOf("server-2-0", "server-2-1"), answerDao.getBySubmissionId("server-2").map { it.id }.sorted())
    }
    @Test
    fun `pulling doesn't revert a survey completed offline back to pending`() = runBlocking {
        // A survey pulled from the server, then completed here but not yet uploaded
        submissionDao.upsertAll(listOf(Submission(id = "server-3", _id = "server-3", _rev = "1-abc", parentId = "exam1@course1", type = "survey", status = "pending")))
        answerDao.upsertAll(listOf(Answer(id = "ans-local", value = "Mine", questionId = "q1", submissionId = "server-3")))
        submissionDao.markComplete("server-3", "{}")

        repository.insertSubmission(pulledSubmission("server-3", status = "pending"))

        val row = submissionDao.getByIdOrRemoteId("server-3")!!
        assertEquals("complete", row.status)
        assertEquals(true, row.isUpdated)
        assertEquals("2-abc", row._rev)
        assertEquals(listOf("Mine"), answerDao.getBySubmissionId("server-3").map { it.value })
        assertEquals(listOf("server-3"), submissionDao.getPendingSubmissions().map { it.id })
    }

    @Test
    fun `pulling doesn't overwrite unsynced changes on an uploaded local submission`() = runBlocking {
        storeUploadedLocalSubmission()
        submissionDao.updateStatus("local-uuid", "requires grading")

        repository.insertSubmission(pulledSubmission("server-1", status = "pending"))

        val row = submissionDao.getByIdOrRemoteId("local-uuid")!!
        assertEquals("requires grading", row.status)
        assertEquals(true, row.isUpdated)
        assertEquals("2-abc", row._rev)
    }

    @Test
    fun `pulling keeps a duplicate completed offline instead of deleting it`() = runBlocking {
        storeUploadedLocalSubmission()
        submissionDao.upsertAll(listOf(Submission(id = "server-1", _id = "server-1", _rev = "1-abc", parentId = "exam1@course1", type = "survey", status = "pending")))
        answerDao.upsertAll(listOf(Answer(id = "ans-offline", value = "Mine", questionId = "q1", submissionId = "server-1")))
        submissionDao.markComplete("server-1", "{}")

        repository.insertSubmission(pulledSubmission("server-1", status = "pending"))

        val row = submissionDao.getByIdOrRemoteId("server-1")!!
        assertEquals("server-1", row.id)
        assertEquals("complete", row.status)
        assertEquals(true, row.isUpdated)
        assertEquals("2-abc", row._rev)
        assertEquals(listOf("Mine"), answerDao.getBySubmissionId("server-1").map { it.value })
        assertEquals(listOf("server-1"), submissionDao.getPendingSubmissions().map { it.id })
        assertEquals(listOf("server-1"), submissionDao.getByRemoteIds(listOf("server-1")).map { it.id })
        assertEquals(emptyList<Answer>(), answerDao.getBySubmissionId("local-uuid"))
    }

    @Test
    fun `pulling still applies server changes to a synced submission`() = runBlocking {
        submissionDao.upsertAll(listOf(Submission(id = "server-4", _id = "server-4", _rev = "1-abc", type = "exam", status = "requires grading")))

        repository.insertSubmission(pulledSubmission("server-4", status = "graded"))

        val row = submissionDao.getByIdOrRemoteId("server-4")!!
        assertEquals("graded", row.status)
        assertEquals("2-abc", row._rev)
    }

    @Test
    fun `pulling keeps each answer's grade`() = runBlocking {
        val graded = pulledSubmission("server-5").apply {
            add("answers", JsonArray().apply {
                add(JsonObject().apply { addProperty("value", "A"); addProperty("questionId", "q1"); addProperty("grade", 1) })
                add(JsonObject().apply { addProperty("value", "B"); addProperty("questionId", "q2"); addProperty("grade", 0) })
            })
        }

        repository.insertSubmission(graded)

        val grades = answerDao.getBySubmissionId("server-5").associate { it.questionId to it.grade }
        assertEquals(mapOf("q1" to 1, "q2" to 0), grades)
    }

    @Test
    fun `pulling leaves an answer with no grade ungraded`() = runBlocking {
        repository.insertSubmission(pulledSubmission("server-6", status = "requires grading"))

        assertEquals(listOf(null, null), answerDao.getBySubmissionId("server-6").map { it.grade })
    }
}
