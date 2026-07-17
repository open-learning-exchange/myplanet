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
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UploadedItemResult

class UploadConfigsTest {
    private val searchActivityDao: SearchActivityDao = mockk(relaxed = true)
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
        apkLogDao = mockk<ApkLogDao>(relaxed = true),
        searchActivityDao = searchActivityDao
    )

    @Test
    fun `SearchActivity config fetches pending Room rows from DAO`() = runTest {
        val pending = listOf(RealmSearchActivity(id = "local-1", text = "math"))
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
}
