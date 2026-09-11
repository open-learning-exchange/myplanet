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
        val answers = (1..1200).map { i ->
            createAnswer("ans_$i", "sub_$i")
        }
        answerDao.upsertAll(answers)

        val allSubmissionIds = answers.map { it.submissionId!! }
        val results = answerDao.getBySubmissionIds(allSubmissionIds)

        assertEquals(1200, results.size)
        assertEquals(answers.map { it.id }.toSet(), results.map { it.id }.toSet())
    }

    @Test
    fun deleteBySubmissionIds_with1200SubmissionIds_deletesAllAndReturnsTotalCount() = runBlocking {
        val answers = (1..1200).map { i ->
            createAnswer("ans_$i", "sub_$i")
        }
        answerDao.upsertAll(answers)

        val allSubmissionIds = answers.map { it.submissionId!! }
        val deletedCount = answerDao.deleteBySubmissionIds(allSubmissionIds)

        assertEquals(1200, deletedCount)

        val remainingAnswers = answerDao.getBySubmissionIds(allSubmissionIds)
        assertTrue(remainingAnswers.isEmpty())
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
