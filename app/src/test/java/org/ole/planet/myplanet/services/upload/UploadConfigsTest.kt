package org.ole.planet.myplanet.services.upload

import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.CourseActivity
import org.ole.planet.myplanet.model.NewsLog
import org.ole.planet.myplanet.model.Rating
import org.ole.planet.myplanet.model.ResourceActivity
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.DiagnosticsRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UploadedItemResult
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.VersionUtils

class UploadConfigsTest {
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val voicesRepository: VoicesRepository = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private lateinit var uploadConfigs: UploadConfigs

    @Before
    fun setup() {
        mockkObject(VersionUtils)
        every { VersionUtils.getAndroidId(any()) } returns "test-android-id"

        mockkObject(NetworkUtils)
        every { NetworkUtils.getDeviceName() } returns "test-device-name"
        every { NetworkUtils.getUniqueIdentifier() } returns "test-unique-id"

        every { sharedPrefManager.getCustomDeviceName() } returns "test-custom-device"

        uploadConfigs = UploadConfigs(
            context = mockk(relaxed = true),
            voicesRepository = voicesRepository,
            submissionsRepository = mockk(relaxed = true),
            activitiesRepository = activitiesRepository,
            teamsSyncRepository = mockk<Lazy<TeamsSyncRepository>>(relaxed = true),
            sharedPrefManager = sharedPrefManager,
            userRepository = mockk(relaxed = true),
            surveysRepository = mockk(relaxed = true),
            feedbackRepository = mockk(relaxed = true),
            ratingsRepository = mockk(relaxed = true),
            eventsRepository = mockk(relaxed = true),
            resourcesRepository = mockk(relaxed = true),
            diagnosticsRepository = mockk<DiagnosticsRepository>(relaxed = true),
            progressRepository = mockk<ProgressRepository>(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `SearchActivity config fetches pending Room rows from DAO`() = runTest {
        val pending = listOf(SearchActivity(id = "local-1", text = "math"))
        coEvery { activitiesRepository.getPendingSearchActivityUploads() } returns pending

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
            activitiesRepository.markSearchActivityUploaded(localId = "local-1", remoteId = "remote-1", rev = "1-rev")
        } returns true

        val failures = uploadConfigs.SearchActivity.markUploaded(listOf(result))

        assertTrue(failures.isEmpty())
        coVerify {
            activitiesRepository.markSearchActivityUploaded(localId = "local-1", remoteId = "remote-1", rev = "1-rev")
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
            activitiesRepository.markSearchActivityUploaded(localId = "missing-local", remoteId = "remote-1", rev = "1-rev")
        } returns false

        val failures = uploadConfigs.SearchActivity.markUploaded(listOf(result))

        assertEquals(listOf(result), failures)
    }

    @Test
    fun `CourseActivities config fetches pending Room rows from DAO`() = runTest {
        val pending = listOf(CourseActivity().apply { id = "course-local-1" })
        coEvery { activitiesRepository.getPendingCourseActivityUploads() } returns pending

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
            activitiesRepository.markCourseActivityUploaded(
                localId = "course-local-1",
                remoteId = "course-remote-1",
                rev = "1-rev"
            )
        } returns true

        val failures = uploadConfigs.CourseActivities.markUploaded(listOf(result))

        assertTrue(failures.isEmpty())
        coVerify {
            activitiesRepository.markCourseActivityUploaded(
                localId = "course-local-1",
                remoteId = "course-remote-1",
                rev = "1-rev"
            )
        }
    }
    @Test
    fun `ResourceActivities config fetches pending Room rows from DAO`() = runTest {
        val pending = listOf(ResourceActivity().apply { id = "resource-local-1" })
        coEvery { activitiesRepository.getPendingResourceActivityUploads() } returns pending

        val result = uploadConfigs.ResourceActivities.fetchPendingItems()

        assertEquals(pending, result)
    }

    @Test
    fun `ResourceActivitiesSync config fetches pending sync Room rows from DAO`() = runTest {
        val pending = listOf(ResourceActivity().apply { id = "resource-sync-local-1"; type = "sync" })
        coEvery { activitiesRepository.getPendingResourceActivitySyncUploads() } returns pending

        val result = uploadConfigs.ResourceActivitiesSync.fetchPendingItems()

        assertEquals(pending, result)
    }

    @Test
    fun `NewsActivities config fetches pending Room rows from DAO`() = runTest {
        val pending = listOf(NewsLog().apply { id = "news-local-1" })
        coEvery { voicesRepository.getPendingNewsLogUploads() } returns pending

        val result = uploadConfigs.NewsActivities.fetchPendingItems()

        assertEquals(pending, result)
    }

    @Test
    fun `NewsActivities serializer includes customDeviceName`() = runTest {
        val newsLog = NewsLog().apply { userId = "user1"; type = "news"; time = 100L }
        val serializer = uploadConfigs.NewsActivities.serializer as UploadSerializer.Simple
        val json = serializer.serialize(newsLog)

        assertEquals("user1", json.get("user").asString)
        assertEquals("test-custom-device", json.get("customDeviceName").asString)
    }

    @Test
    fun `SearchActivity serializer includes androidId and customDeviceName`() = runTest {
        val search = SearchActivity(id = "s1", text = "query")
        val serializer = uploadConfigs.SearchActivity.serializer as UploadSerializer.Simple
        val json = serializer.serialize(search)

        assertEquals("query", json.get("text").asString)
        assertEquals("test-android-id", json.get("androidId").asString)
        assertEquals("test-custom-device", json.get("customDeviceName").asString)
    }

    @Test
    fun `Rating serializer includes customDeviceName`() = runTest {
        val rating = Rating().apply { rate = 5; user = "{}" }
        val serializer = uploadConfigs.Rating.serializer as UploadSerializer.Simple
        val json = serializer.serialize(rating)

        assertEquals(5, json.get("rate").asInt)
        assertEquals("test-custom-device", json.get("customDeviceName").asString)
    }

}
