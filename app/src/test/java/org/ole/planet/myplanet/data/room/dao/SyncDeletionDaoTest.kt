package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.Achievement
import org.ole.planet.myplanet.model.Certification
import org.ole.planet.myplanet.model.ChatHistory
import org.ole.planet.myplanet.model.CourseProgress
import org.ole.planet.myplanet.model.Feedback
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.Rating
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.model.TeamTask
import org.robolectric.annotation.Config

/**
 * Covers the deleteByIds methods added for the incremental-sync deletion extension (see
 * TransactionSyncManager.deleteBatch). One shared in-memory database, one test per DAO: upsert
 * two rows, delete one by id, assert the other survives untouched.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [32])
class SyncDeletionDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun achievementDao_deleteByIds_removesOnlyTargetedRows() = runBlocking {
        val dao = database.achievementDao()
        dao.upsertAll(listOf(Achievement().apply { _id = "a1" }, Achievement().apply { _id = "a2" }))

        dao.deleteByIds(listOf("a1"))

        assertEquals(null, dao.getById("a1"))
        assertEquals("a2", dao.getById("a2")?._id)
    }

    @Test
    fun tagDao_deleteByIds_removesOnlyTargetedRows() = runBlocking {
        val dao = database.tagDao()
        dao.upsertAll(listOf(TagEntity().apply { id = "t1" }, TagEntity().apply { id = "t2" }))

        dao.deleteByIds(listOf("t1"))

        assertEquals(emptyList<TagEntity>(), dao.getByIds(listOf("t1")))
        assertEquals(1, dao.getByIds(listOf("t2")).size)
    }

    @Test
    fun feedbackDao_deleteByIds_removesOnlyTargetedRows() = runBlocking {
        val dao = database.feedbackDao()
        dao.upsertAll(listOf(Feedback().apply { id = "f1" }, Feedback().apply { id = "f2" }))

        dao.deleteByIds(listOf("f1"))

        assertEquals(null, dao.findById("f1"))
        assertEquals("f2", dao.findById("f2")?.id)
    }

    @Test
    fun healthExaminationDao_deleteByIds_removesOnlyTargetedRows() = runBlocking {
        val dao = database.healthExaminationDao()
        dao.upsertAll(listOf(HealthExamination().apply { _id = "h1" }, HealthExamination().apply { _id = "h2" }))

        dao.deleteByIds(listOf("h1"))

        assertEquals(null, dao.getById("h1"))
        assertEquals("h2", dao.getById("h2")?._id)
    }

    @Test
    fun chatDao_deleteByIds_removesOnlyTargetedRows() = runBlocking {
        val dao = database.chatDao()
        dao.upsertAll(listOf(ChatHistory().apply { id = "c1" }, ChatHistory().apply { id = "c2" }))

        dao.deleteByIds(listOf("c1"))

        assertEquals(emptyList<ChatHistory>(), dao.getByDocId("c1"))
    }

    @Test
    fun meetupDao_deleteByIds_removesOnlyTargetedRows() = runBlocking {
        val dao = database.meetupDao()
        dao.upsertAll(listOf(Meetup().apply { id = "m1" }, Meetup().apply { id = "m2" }))

        dao.deleteByIds(listOf("m1"))

        assertEquals(null, dao.getById("m1"))
        assertEquals("m2", dao.getById("m2")?.id)
    }

    @Test
    fun certificationDao_deleteByIds_removesOnlyTargetedRows() = runBlocking {
        val dao = database.certificationDao()
        dao.upsertAll(listOf(
            Certification().apply { _id = "cert1" },
            Certification().apply { _id = "cert2" }
        ))

        dao.deleteByIds(listOf("cert1"))

        assertEquals(0, dao.countByCourseId("cert1"))
    }

    @Test
    fun courseDao_deleteByIds_removesOnlyTargetedRows() = runBlocking {
        val dao = database.courseDao()
        dao.upsertAll(listOf(MyCourse(id = "course1"), MyCourse(id = "course2")))

        dao.deleteByIds(listOf("course1"))

        assertEquals(null, dao.getByCourseId("course1"))
        assertEquals("course2", dao.getByCourseId("course2")?.id)
    }

    @Test
    fun teamTaskDao_deleteByIds_removesOnlyTargetedRows() = runBlocking {
        val dao = database.teamTaskDao()
        dao.upsertAll(listOf(TeamTask().apply { id = "task1" }, TeamTask().apply { id = "task2" }))

        dao.deleteByIds(listOf("task1"))

        assertEquals(null, dao.getById("task1"))
        assertEquals("task2", dao.getById("task2")?.id)
    }

    @Test
    fun examDao_deleteByIds_removesOnlyTargetedRows() = runBlocking {
        val dao = database.examDao()
        dao.upsertAll(listOf(StepExam(id = "exam1"), StepExam(id = "exam2")))

        dao.deleteByIds(listOf("exam1"))

        assertEquals(null, dao.getById("exam1"))
        assertEquals("exam2", dao.getById("exam2")?.id)
    }

    @Test
    fun ratingDao_deleteByIds_removesOnlyTargetedRows() = runBlocking {
        val dao = database.ratingDao()
        dao.upsertAll(listOf(Rating().apply { id = "r1" }, Rating().apply { id = "r2" }))

        dao.deleteByIds(listOf("r1"))

        assertEquals(null, dao.findById("r1"))
        assertEquals("r2", dao.findById("r2")?.id)
    }

    @Test
    fun courseProgressDao_deleteByIds_matchesOnCouchIdNotLocalId() = runBlocking {
        val dao = database.courseProgressDao()
        // A synced row (_id set) and a locally-pending row (_id null, per getPendingUploads'
        // own "_id IS NULL" check) sharing no relation -- deleting by couch id must only ever
        // touch the synced row, never a pending upload.
        dao.upsertAll(listOf(
            CourseProgress().apply { id = "local1"; _id = "synced1" },
            CourseProgress().apply { id = "local2"; _id = null }
        ))

        dao.deleteByIds(listOf("synced1"))

        assertEquals(emptyList<CourseProgress>(), dao.getByIds(listOf("local1")))
        assertEquals(1, dao.getByIds(listOf("local2")).size)
    }
}
