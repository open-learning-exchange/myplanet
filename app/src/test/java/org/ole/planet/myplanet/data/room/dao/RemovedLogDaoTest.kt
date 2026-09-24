package org.ole.planet.myplanet.data.room.dao

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
import org.ole.planet.myplanet.model.RemovedLog

@RunWith(AndroidJUnit4::class)
class RemovedLogDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var removedLogDao: RemovedLogDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        removedLogDao = database.removedLogDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun createLog(
        id: String,
        type: String? = "courses",
        userId: String? = "user1",
        docId: String? = "doc1"
    ) = RemovedLog().apply {
        this.id = id
        this.type = type
        this.userId = userId
        this.docId = docId
    }

    @Test
    fun deleteByTypeUserAndDoc_nonNullUserId_deletesOnlyMatchingRow() = runBlocking {
        val target = createLog(id = "1", type = "courses", userId = "user1", docId = "doc1")
        val otherUser = createLog(id = "2", type = "courses", userId = "user2", docId = "doc1")
        val otherType = createLog(id = "3", type = "resources", userId = "user1", docId = "doc1")
        val otherDoc = createLog(id = "4", type = "courses", userId = "user1", docId = "doc2")

        removedLogDao.insertAll(listOf(target, otherUser, otherType, otherDoc))

        removedLogDao.deleteByTypeUserAndDoc("courses", "user1", "doc1")

        val user1Docs = removedLogDao.getRemovedDocIds("courses", "user1")
        val user2Docs = removedLogDao.getRemovedDocIds("courses", "user2")
        val user1ResourceDocs = removedLogDao.getRemovedDocIds("resources", "user1")

        assertEquals(listOf("doc2"), user1Docs)
        assertEquals(listOf("doc1"), user2Docs)
        assertEquals(listOf("doc1"), user1ResourceDocs)
    }

    @Test
    fun deleteByTypeUserAndDoc_nullUserId_deletesNullUserRowAndLeavesNonNullUserRow() = runBlocking {
        val nullUserLog = createLog(id = "1", type = "courses", userId = null, docId = "doc1")
        val nonNullUserLog = createLog(id = "2", type = "courses", userId = "user1", docId = "doc1")

        removedLogDao.insertAll(listOf(nullUserLog, nonNullUserLog))

        removedLogDao.deleteByTypeUserAndDoc("courses", null, "doc1")

        val nullUserDocs = removedLogDao.getRemovedDocIds("courses", null)
        val nonNullUserDocs = removedLogDao.getRemovedDocIds("courses", "user1")

        assertTrue(nullUserDocs.isEmpty())
        assertEquals(listOf("doc1"), nonNullUserDocs)
    }

    @Test
    fun deleteByTypeUserAndDocsChunked_1200Docs_deletesAllAndLeavesOtherTypeAndUser() = runBlocking {
        val docIds = (1..1200).map { "doc_$it" }
        val logsToDelete = docIds.map { docId ->
            createLog(id = "del_$docId", type = "courses", userId = "user1", docId = docId)
        }
        val otherUserLog = createLog(id = "other_user", type = "courses", userId = "user2", docId = "doc_1")
        val otherTypeLog = createLog(id = "other_type", type = "resources", userId = "user1", docId = "doc_1")

        removedLogDao.insertAll(logsToDelete + listOf(otherUserLog, otherTypeLog))

        removedLogDao.deleteByTypeUserAndDocsChunked("courses", "user1", docIds)

        val user1CourseDocs = removedLogDao.getRemovedDocIds("courses", "user1")
        val user2CourseDocs = removedLogDao.getRemovedDocIds("courses", "user2")
        val user1ResourceDocs = removedLogDao.getRemovedDocIds("resources", "user1")

        assertTrue(user1CourseDocs.isEmpty())
        assertEquals(listOf("doc_1"), user2CourseDocs)
        assertEquals(listOf("doc_1"), user1ResourceDocs)
    }

    @Test
    fun deleteByTypeUserAndDocsChunked_nullUserId_1200Docs_deletesAllMatchingAndLeavesNonNullUser() = runBlocking {
        val docIds = (1..1200).map { "doc_$it" }
        val logsToDelete = docIds.map { docId ->
            createLog(id = "del_null_$docId", type = "courses", userId = null, docId = docId)
        }
        val nonNullUserLog = createLog(id = "non_null_user", type = "courses", userId = "user1", docId = "doc_1")
        val otherTypeNullUserLog = createLog(id = "other_type_null", type = "resources", userId = null, docId = "doc_1")

        removedLogDao.insertAll(logsToDelete + listOf(nonNullUserLog, otherTypeNullUserLog))

        removedLogDao.deleteByTypeUserAndDocsChunked("courses", null, docIds)

        val nullUserCourseDocs = removedLogDao.getRemovedDocIds("courses", null)
        val nonNullUserCourseDocs = removedLogDao.getRemovedDocIds("courses", "user1")
        val nullUserResourceDocs = removedLogDao.getRemovedDocIds("resources", null)

        assertTrue(nullUserCourseDocs.isEmpty())
        assertEquals(listOf("doc_1"), nonNullUserCourseDocs)
        assertEquals(listOf("doc_1"), nullUserResourceDocs)
    }

    @Test
    fun getRemovedDocIds_returnsDocIdsForGivenTypeAndUserId() = runBlocking {
        val user1CourseLog1 = createLog(id = "1", type = "courses", userId = "user1", docId = "doc1")
        val user1CourseLog2 = createLog(id = "2", type = "courses", userId = "user1", docId = "doc2")
        val nullUserCourseLog = createLog(id = "3", type = "courses", userId = null, docId = "doc3")
        val user1ResourceLog = createLog(id = "4", type = "resources", userId = "user1", docId = "doc4")

        removedLogDao.insertAll(listOf(user1CourseLog1, user1CourseLog2, nullUserCourseLog, user1ResourceLog))

        val user1CourseDocs = removedLogDao.getRemovedDocIds("courses", "user1")
        val nullUserCourseDocs = removedLogDao.getRemovedDocIds("courses", null)

        assertEquals(listOf("doc1", "doc2"), user1CourseDocs)
        assertEquals(listOf("doc3"), nullUserCourseDocs)
    }
}
