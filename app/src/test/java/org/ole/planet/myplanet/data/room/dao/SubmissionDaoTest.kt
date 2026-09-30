package org.ole.planet.myplanet.data.room.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.Submission
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SubmissionDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var submissionDao: SubmissionDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        submissionDao = database.submissionDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun getPendingByUserAndParent_returnsLatestByStartTimeDescNotLastUpdateTime() = runBlocking {
        // sub1: started earliest (100), updated latest (500)
        val sub1 = Submission(id = "sub1", parentId = "parent1", userId = "user1", status = "pending", startTime = 100L, lastUpdateTime = 500L)
        // sub2: started latest (300), updated earlier (200)
        val sub2 = Submission(id = "sub2", parentId = "parent1", userId = "user1", status = "pending", startTime = 300L, lastUpdateTime = 200L)
        // sub3: started middle (200), updated middle (300)
        val sub3 = Submission(id = "sub3", parentId = "parent1", userId = "user1", status = "pending", startTime = 200L, lastUpdateTime = 300L)

        submissionDao.upsertAll(listOf(sub1, sub2, sub3))

        val result = submissionDao.getPendingByUserAndParent("parent1", "user1")

        assertNotNull(result)
        // Assert that sub2 comes back because its startTime (300L) is the highest,
        // catching any ordering change (e.g. if it were ordered by lastUpdateTime, sub1 with 500L would return).
        assertEquals("sub2", result!!.id)
    }

    @Test
    fun getPendingByUserAndParent_handlesNullUserIdAndNullParentIdWithIsSemantics() = runBlocking {
        val subNullUserAndParent = Submission(id = "sub_null_both", parentId = null, userId = null, status = "pending", startTime = 100L)
        val subNullUser = Submission(id = "sub_null_user", parentId = "parent1", userId = null, status = "pending", startTime = 200L)
        val subWithUser = Submission(id = "sub_with_user", parentId = "parent1", userId = "user1", status = "pending", startTime = 300L)

        submissionDao.upsertAll(listOf(subNullUserAndParent, subNullUser, subWithUser))

        val resultNullUser = submissionDao.getPendingByUserAndParent("parent1", null)
        assertNotNull(resultNullUser)
        assertEquals("sub_null_user", resultNullUser!!.id)

        val resultNullBoth = submissionDao.getPendingByUserAndParent(null, null)
        assertNotNull(resultNullBoth)
        assertEquals("sub_null_both", resultNullBoth!!.id)

        val resultWithUser = submissionDao.getPendingByUserAndParent("parent1", "user1")
        assertNotNull(resultWithUser)
        assertEquals("sub_with_user", resultWithUser!!.id)
    }

    @Test
    fun getPendingByUserAndParent_ignoresNonPendingOrMismatchingRows() = runBlocking {
        val completedSub = Submission(id = "sub_complete", parentId = "parent1", userId = "user1", status = "complete", startTime = 400L)
        val otherParentSub = Submission(id = "sub_other_parent", parentId = "parent2", userId = "user1", status = "pending", startTime = 500L)

        submissionDao.upsertAll(listOf(completedSub, otherParentSub))

        val result = submissionDao.getPendingByUserAndParent("parent1", "user1")
        assertNull(result)
    }

    @Test
    fun getByParentIdsAndTeamId_handlesMoreThan1000ParentIdsAndDuplicatesWithoutThrowing() = runBlocking {
        val teamId = "team1"
        val submissions = (0 until 1200).map { i ->
            Submission(id = "sub_$i", parentId = "parent_$i", teamId = teamId)
        }
        submissionDao.upsertAll(submissions)

        val parentIds = (0 until 1200).map { "parent_$it" }
        val parentIdsWithDuplicates = parentIds + parentIds.take(100)

        val result = submissionDao.getByParentIdsAndTeamId(parentIdsWithDuplicates, teamId)

        assertEquals(1200, result.size)
        assertEquals(1200, result.map { it.id }.distinct().size)

        val emptyResult = submissionDao.getByParentIdsAndTeamId(emptyList(), teamId)
        assertEquals(0, emptyResult.size)
    }
}
