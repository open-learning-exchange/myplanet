package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.HealthExamination

@RunWith(AndroidJUnit4::class)
class HealthExaminationDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var healthExaminationDao: HealthExaminationDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        healthExaminationDao = database.healthExaminationDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun createExam(id: String, rev: String? = null, isUpdated: Boolean = true): HealthExamination {
        return HealthExamination().apply {
            this._id = id
            this._rev = rev
            this.isUpdated = isUpdated
            this.userId = "user_1"
        }
    }

    @Test
    fun markUploaded_rowsWithRev_updatesRevAndClearsIsUpdated() = runBlocking {
        val exam1 = createExam("exam1", rev = "rev_old_1", isUpdated = true)
        val exam2 = createExam("exam2", rev = "rev_old_2", isUpdated = true)
        healthExaminationDao.upsertAll(listOf(exam1, exam2))

        val map = mapOf("exam1" to "rev_new_1", "exam2" to "rev_new_2")
        healthExaminationDao.markUploaded(map)

        val updated1 = healthExaminationDao.getById("exam1")
        val updated2 = healthExaminationDao.getById("exam2")

        assertNotNull(updated1)
        assertEquals("rev_new_1", updated1!!._rev)
        assertFalse(updated1.isUpdated)

        assertNotNull(updated2)
        assertEquals("rev_new_2", updated2!!._rev)
        assertFalse(updated2.isUpdated)
    }

    @Test
    fun markUploaded_rowsWithoutRev_clearsIsUpdatedAndPreservesExistingRev() = runBlocking {
        val exam1 = createExam("exam1", rev = "rev_existing_1", isUpdated = true)
        val exam2 = createExam("exam2", rev = null, isUpdated = true)
        healthExaminationDao.upsertAll(listOf(exam1, exam2))

        val map = mapOf<String, String?>("exam1" to null, "exam2" to null)
        healthExaminationDao.markUploaded(map)

        val updated1 = healthExaminationDao.getById("exam1")
        val updated2 = healthExaminationDao.getById("exam2")

        assertNotNull(updated1)
        assertEquals("rev_existing_1", updated1!!._rev)
        assertFalse(updated1.isUpdated)

        assertNotNull(updated2)
        assertEquals(null, updated2!!._rev)
        assertFalse(updated2.isUpdated)
    }

    @Test
    fun markUploaded_mixedBatch_handlesBothNullAndNonNullRevsCorrectly() = runBlocking {
        val exam1 = createExam("exam1", rev = "rev_1", isUpdated = true)
        val exam2 = createExam("exam2", rev = "rev_2", isUpdated = true)
        val exam3 = createExam("exam3", rev = "rev_3", isUpdated = true)
        healthExaminationDao.upsertAll(listOf(exam1, exam2, exam3))

        val map = mapOf(
            "exam1" to null,
            "exam2" to "rev_2_updated",
            "exam3" to null
        )
        healthExaminationDao.markUploaded(map)

        val updated1 = healthExaminationDao.getById("exam1")
        val updated2 = healthExaminationDao.getById("exam2")
        val updated3 = healthExaminationDao.getById("exam3")

        assertNotNull(updated1)
        assertEquals("rev_1", updated1!!._rev)
        assertFalse(updated1.isUpdated)

        assertNotNull(updated2)
        assertEquals("rev_2_updated", updated2!!._rev)
        assertFalse(updated2.isUpdated)

        assertNotNull(updated3)
        assertEquals("rev_3", updated3!!._rev)
        assertFalse(updated3.isUpdated)
    }

    @Test
    fun markUploaded_emptyMap_noOp() = runBlocking {
        val exam = createExam("exam1", rev = "rev_1", isUpdated = true)
        healthExaminationDao.upsert(exam)

        healthExaminationDao.markUploaded(emptyMap())

        val result = healthExaminationDao.getById("exam1")
        assertNotNull(result)
        assertEquals("rev_1", result!!._rev)
        assertEquals(true, result.isUpdated)
    }
}
