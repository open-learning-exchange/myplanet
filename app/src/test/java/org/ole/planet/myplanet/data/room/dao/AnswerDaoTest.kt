package org.ole.planet.myplanet.data.room.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.Answer
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AnswerDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var answerDao: AnswerDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        answerDao = database.answerDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun createAnswer(id: String, submissionId: String): Answer {
        return Answer(id = id, submissionId = submissionId)
    }

    @Test
    fun getBySubmissionIds_with1200SubmissionIds_returnsAllAnswersWithoutThrowing() = runBlocking {
        val seededAnswers = listOf(
            createAnswer("ans_1", "sub_1"),
            createAnswer("ans_900", "sub_900"),
            createAnswer("ans_901", "sub_901"),
            createAnswer("ans_1200", "sub_1200"),
            createAnswer("ans_survivor", "sub_survivor")
        )
        answerDao.upsertAll(seededAnswers)

        val allSubmissionIds = (1..1200).map { "sub_$it" }
        val results = answerDao.getBySubmissionIds(allSubmissionIds)

        assertEquals(4, results.size)
        assertEquals(setOf("ans_1", "ans_900", "ans_901", "ans_1200"), results.map { it.id }.toSet())
    }

    @Test
    fun deleteBySubmissionIds_with1200SubmissionIds_deletesAllAndReturnsTotalCount() = runBlocking {
        val seededAnswers = listOf(
            createAnswer("ans_1", "sub_1"),
            createAnswer("ans_900", "sub_900"),
            createAnswer("ans_901", "sub_901"),
            createAnswer("ans_1200", "sub_1200"),
            createAnswer("ans_survivor", "sub_survivor")
        )
        answerDao.upsertAll(seededAnswers)

        val allSubmissionIds = (1..1200).map { "sub_$it" }
        val deletedCount = answerDao.deleteBySubmissionIds(allSubmissionIds)

        assertEquals(4, deletedCount)

        val remainingAnswers = answerDao.getBySubmissionIds(allSubmissionIds)
        assertTrue(remainingAnswers.isEmpty())

        val survivor = answerDao.getBySubmissionId("sub_survivor")
        assertEquals(1, survivor.size)
        assertEquals("ans_survivor", survivor.first().id)
    }

    @Test
    fun getBySubmissionIds_and_deleteBySubmissionIds_withEmptyList_shortCircuitAndReturnEmptyOrZero() = runBlocking {
        val answer = createAnswer("ans_1", "sub_1")
        answerDao.upsertAll(listOf(answer))

        val selectResult = answerDao.getBySubmissionIds(emptyList())
        assertTrue(selectResult.isEmpty())

        val deleteCount = answerDao.deleteBySubmissionIds(emptyList())
        assertEquals(0, deleteCount)

        // Verify existing answer in database was not affected
        val existing = answerDao.getBySubmissionId("sub_1")
        assertEquals(1, existing.size)
    }

    @Test
    fun chunkedOperations_deduplicateRepeatedIdsAcrossChunkBoundary() = runBlocking {
        val seededAnswers = listOf(
            createAnswer("ans_1", "sub_1"),
            createAnswer("ans_900", "sub_900"),
            createAnswer("ans_901", "sub_901"),
            createAnswer("ans_1000", "sub_1000"),
            createAnswer("ans_survivor", "sub_survivor")
        )
        answerDao.upsertAll(seededAnswers)

        // Create a list of 1001 submissionIds where "sub_1" appears at index 0 (chunk 1) and index 1000 (chunk 2)
        val submissionIdsWithDuplicate = (1..1000).map { "sub_$it" } + "sub_1"
        assertEquals(1001, submissionIdsWithDuplicate.size)

        val results = answerDao.getBySubmissionIds(submissionIdsWithDuplicate)
        assertEquals(4, results.size)
        assertEquals(setOf("ans_1", "ans_900", "ans_901", "ans_1000"), results.map { it.id }.toSet())

        val deleteCount = answerDao.deleteBySubmissionIds(submissionIdsWithDuplicate)
        assertEquals(4, deleteCount)

        val remaining = answerDao.getBySubmissionIds(submissionIdsWithDuplicate)
        assertTrue(remaining.isEmpty())

        val survivor = answerDao.getBySubmissionId("sub_survivor")
        assertEquals(1, survivor.size)
        assertEquals("ans_survivor", survivor.first().id)
    }

    @Test
    fun getBySubmissionIds_and_deleteBySubmissionIds_withMixedList_handlesExistingAndNonExistingIds() = runBlocking {
        val answers = (1..5).map { i ->
            createAnswer("ans_$i", "sub_$i")
        }
        answerDao.upsertAll(answers)

        val querySubmissionIds = listOf("sub_1", "sub_3", "non_existing_1", "non_existing_2")
        val selectResults = answerDao.getBySubmissionIds(querySubmissionIds)

        assertEquals(2, selectResults.size)
        assertEquals(setOf("ans_1", "ans_3"), selectResults.map { it.id }.toSet())

        val deleteCount = answerDao.deleteBySubmissionIds(querySubmissionIds)
        assertEquals(2, deleteCount)

        val remaining = answerDao.getBySubmissionIds(listOf("sub_1", "sub_2", "sub_3", "sub_4", "sub_5"))
        assertEquals(3, remaining.size)
        assertEquals(setOf("ans_2", "ans_4", "ans_5"), remaining.map { it.id }.toSet())
    }
}
