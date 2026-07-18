package org.ole.planet.myplanet.services.upload

import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.ApkLogDao
import org.ole.planet.myplanet.data.room.dao.CourseActivityDao
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.NewsLogDao
import org.ole.planet.myplanet.data.room.dao.ResourceActivityDao
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.data.room.dao.SubmitPhotosDao
import org.ole.planet.myplanet.data.room.dao.TeamLogDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.model.CourseActivity
import org.ole.planet.myplanet.model.NewsLog
import org.ole.planet.myplanet.model.ResourceActivity
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UploadedItemResult

class UploadConfigsTest {
    private val searchActivityDao: SearchActivityDao = mockk(relaxed = true)
    private val courseActivityDao: CourseActivityDao = mockk(relaxed = true)
    private val courseProgressDao: CourseProgressDao = mockk(relaxed = true)
    private val newsLogDao: NewsLogDao = mockk(relaxed = true)
    private val resourceActivityDao: ResourceActivityDao = mockk(relaxed = true)
    private val submitPhotosDao: SubmitPhotosDao = mockk(relaxed = true)
    private val teamLogDao: TeamLogDao = mockk(relaxed = true)
    private val uploadConfigs = UploadConfigs(
        voicesRepository = mockk(relaxed = true),
        submissionsRepository = mockk(relaxed = true),
        activitiesRepository = mockk(relaxed = true),
        teamsSyncRepository = mockk<Lazy<TeamsSyncRepository>>(relaxed = true),
        sharedPrefManager = mockk(relaxed = true),
        userRepository = mockk(relaxed = true),
        surveysRepository = mockk(relaxed = true),
        feedbackRepository = mockk(relaxed = true),
        ratingsRepository = mockk(relaxed = true),
        eventsRepository = mockk(relaxed = true),
        apkLogDao = mockk<ApkLogDao>(relaxed = true),
        searchActivityDao = searchActivityDao,
        courseActivityDao = courseActivityDao,
        courseProgressDao = courseProgressDao,
        resourceActivityDao = resourceActivityDao,
        submitPhotosDao = submitPhotosDao,
        newsLogDao = newsLogDao,
        teamLogDao = teamLogDao,
        teamTaskDao = mockk<TeamTaskDao>(relaxed = true)
    )

    @Test
    fun `SearchActivity config fetches pending Room rows from DAO`() = runTest {
        val pending = listOf(SearchActivity(id = "local-1", text = "math"))
        coEvery { searchActivityDao.getPendingUploads() } returns pending

        val result = uploadConfigs.SearchActivity.fetchPendingItems()

        assertEquals(pending, result)
    }

    @Test
    fun `SearchActivity config marks successful uploads with remote id and rev`() = runTest {
        val result = UploadedItemResult(
            localId = "local-1",
            remoteId = "remote-1",
            remoteRev = "1-rev",
            response = mockk(relaxed = true)
        )
        coEvery {
            searchActivityDao.markUploaded(localId = "local-1", remoteId = "remote-1", rev = "1-rev")
        } returns 1

        val failures = uploadConfigs.SearchActivity.markUploaded(listOf(result))

        assertTrue(failures.isEmpty())
        coVerify {
            searchActivityDao.markUploaded(localId = "local-1", remoteId = "remote-1", rev = "1-rev")
        }
    }

    @Test
    fun `SearchActivity config reports rows that cannot be marked uploaded`() = runTest {
        val result = UploadedItemResult(
            localId = "missing-local",
            remoteId = "remote-1",
            remoteRev = "1-rev",
            response = mockk(relaxed = true)
        )
        coEvery {
            searchActivityDao.markUploaded(localId = "missing-local", remoteId = "remote-1", rev = "1-rev")
        } returns 0

        val failures = uploadConfigs.SearchActivity.markUploaded(listOf(result))

        assertEquals(listOf(result), failures)
    }

    @Test
    fun `CourseActivities config fetches pending Room rows from DAO`() = runTest {
        val pending = listOf(CourseActivity().apply { id = "course-local-1" })
        coEvery { courseActivityDao.getPendingUploads() } returns pending

        val result = uploadConfigs.CourseActivities.fetchPendingItems()

        assertEquals(pending, result)
    }

    @Test
    fun `CourseActivities config marks successful uploads with remote id and rev`() = runTest {
        val result = UploadedItemResult(
            localId = "course-local-1",
            remoteId = "course-remote-1",
            remoteRev = "1-rev",
            response = mockk(relaxed = true)
        )
        coEvery {
            courseActivityDao.markUploaded(
                localId = "course-local-1",
                remoteId = "course-remote-1",
                rev = "1-rev"
            )
        } returns 1

        val failures = uploadConfigs.CourseActivities.markUploaded(listOf(result))

        assertTrue(failures.isEmpty())
        coVerify {
            courseActivityDao.markUploaded(
                localId = "course-local-1",
                remoteId = "course-remote-1",
                rev = "1-rev"
            )
        }
    }
    @Test
    fun `ResourceActivities config fetches pending Room rows from DAO`() = runTest {
        val pending = listOf(ResourceActivity().apply { id = "resource-local-1" })
        coEvery { resourceActivityDao.getPendingUploads() } returns pending

        val result = uploadConfigs.ResourceActivities.fetchPendingItems()

        assertEquals(pending, result)
    }

    @Test
    fun `ResourceActivitiesSync config fetches pending sync Room rows from DAO`() = runTest {
        val pending = listOf(ResourceActivity().apply { id = "resource-sync-local-1"; type = "sync" })
        coEvery { resourceActivityDao.getPendingSyncUploads() } returns pending

        val result = uploadConfigs.ResourceActivitiesSync.fetchPendingItems()

        assertEquals(pending, result)
    }

    @Test
    fun `NewsActivities config fetches pending Room rows from DAO`() = runTest {
        val pending = listOf(NewsLog().apply { id = "news-local-1" })
        coEvery { newsLogDao.getPendingUploads() } returns pending

        val result = uploadConfigs.NewsActivities.fetchPendingItems()

        assertEquals(pending, result)
    }

}
