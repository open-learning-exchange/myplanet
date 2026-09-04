package org.ole.planet.myplanet.services.upload

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.repository.TeamUploadData
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils

@OptIn(ExperimentalCoroutinesApi::class)
class TeamsUploadRunnerTest {

    private val context: Context = mockk(relaxed = true)
    private val teamsSyncRepository: Lazy<TeamsSyncRepository> = mockk(relaxed = true)
    private val uploadRepository: UploadRepository = mockk(relaxed = true)
    private val retryQueue: RetryQueue = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var teamsUploadRunner: TeamsUploadRunner

    @Before
    fun setup() {
        mockkStatic(Log::class)
        io.mockk.mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "http://mock.url"
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        teamsUploadRunner = TeamsUploadRunner(
            context,
            teamsSyncRepository,
            uploadRepository,
            retryQueue,
            TestDispatcherProvider(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
        io.mockk.unmockkObject(UrlUtils)
    }

    @Test
    fun `uploadTeams handles bulk success`() = testScope.runTest {
        val mockTeam1 = TeamUploadData("team1", JsonObject(), false, null)
        val mockTeam2 = TeamUploadData("team2", JsonObject(), false, null)
        val mockTeam3 = TeamUploadData("team3", JsonObject(), true, null)
        val mockRepo = mockk<TeamsSyncRepository>(relaxed = true)
        every { teamsSyncRepository.get() } returns mockRepo
        coEvery { mockRepo.getTeamsForUpload() } returns listOf(mockTeam1, mockTeam2, mockTeam3)

        val bulkResponse = com.google.gson.JsonArray().apply {
            add(JsonObject().apply { addProperty("id", "team1"); addProperty("rev", "rev1") })
            add(JsonObject().apply { addProperty("id", "team2"); addProperty("error", "conflict") })
            add(JsonObject().apply { addProperty("id", "team3"); addProperty("rev", "rev3") })
        }
        coEvery { uploadRepository.postUploadArray(any(), any()) } returns retrofit2.Response.success(bulkResponse)

        coEvery { retryQueue.queueFailedOperation(any(), any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { mockRepo.markTeamsUploaded(any()) } returns Unit
        coEvery { mockRepo.deleteLocalTeamRecords(any()) } returns Unit

        teamsUploadRunner.uploadTeams()
        advanceUntilIdle()

        coVerify(exactly = 1) { uploadRepository.postUploadArray("http://mock.url/teams/_bulk_docs", any()) }
        coVerify(exactly = 1) { mockRepo.markTeamsUploaded(mapOf("team1" to "rev1")) }
        coVerify(exactly = 1) { mockRepo.deleteLocalTeamRecords(listOf("team3")) }
    }
}
