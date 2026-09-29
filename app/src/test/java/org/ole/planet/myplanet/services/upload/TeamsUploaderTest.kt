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
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.repository.TeamUploadData
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils

@OptIn(ExperimentalCoroutinesApi::class)
class TeamsUploaderTest {

    private val context: Context = mockk(relaxed = true)
    private val teamsSyncRepository: Lazy<TeamsSyncRepository> = mockk(relaxed = true)
    private val uploadRepository: UploadRepository = mockk(relaxed = true)
    private val retryQueue: RetryQueue = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var teamsUploader: TeamsUploader

    @Before
    fun setup() {
        mockkStatic(Log::class)
        mockkStatic(android.net.Uri::class)
        every { android.net.Uri.encode(any()) } answers { firstArg() }
        mockkStatic(org.ole.planet.myplanet.utils.FileUtils::class)
        every { org.ole.planet.myplanet.utils.FileUtils.getMimeType(any()) } returns "image/png"
        io.mockk.mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "http://mock.url"
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        teamsUploader = TeamsUploader(
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
    fun `uploadTeams handles bulk success`() = runTest(testDispatcher) {
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

        teamsUploader.uploadTeams()
        advanceUntilIdle()

        coVerify(exactly = 1) { uploadRepository.postUploadArray("http://mock.url/teams/_bulk_docs", any()) }
        coVerify(exactly = 1) { mockRepo.markTeamsUploaded(mapOf("team1" to "rev1")) }
        coVerify(exactly = 1) { mockRepo.deleteLocalTeamRecords(listOf("team3")) }
        coVerify(exactly = 0) { retryQueue.queueFailedOperation(uploadType = "MyTeam", error = any(), payload = any(), endpoint = "teams", httpMethod = "POST", dbId = "team2", modelClassName = "MyTeam") }
    }

    @Test
    fun `uploadTeams keys uploadedTeams by local team id when response id differs`() = runTest(testDispatcher) {
        val mockTeam = TeamUploadData("localTeam1", JsonObject(), false, null)
        val mockRepo = mockk<TeamsSyncRepository>(relaxed = true)
        every { teamsSyncRepository.get() } returns mockRepo
        coEvery { mockRepo.getTeamsForUpload() } returns listOf(mockTeam)

        val bulkResponse = com.google.gson.JsonArray().apply {
            add(JsonObject().apply { addProperty("id", "serverGeneratedId1"); addProperty("rev", "rev1") })
        }
        coEvery { uploadRepository.postUploadArray(any(), any()) } returns retrofit2.Response.success(bulkResponse)
        coEvery { mockRepo.markTeamsUploaded(any()) } returns Unit

        teamsUploader.uploadTeams()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepo.markTeamsUploaded(mapOf("localTeam1" to "rev1")) }
    }

    @Test
    fun `uploadTeams handles bulk network failure`() = runTest(testDispatcher) {
        val mockRepo = mockk<TeamsSyncRepository>(relaxed = true)
        every { teamsSyncRepository.get() } returns mockRepo

        val mockTeam = TeamUploadData("team1", JsonObject(), false, null)
        coEvery { mockRepo.getTeamsForUpload() } returns listOf(mockTeam)

        val errorBody = okhttp3.ResponseBody.create(null, "Error")
        coEvery { uploadRepository.postUploadArray(any(), any()) } returns retrofit2.Response.error(500, errorBody)
        coEvery { retryQueue.queueFailedOperation(any(), any(), any(), any(), any(), any(), any()) } returns Unit

        teamsUploader.uploadTeams()
        advanceUntilIdle()

        coVerify(exactly = 1) { uploadRepository.postUploadArray("http://mock.url/teams/_bulk_docs", any()) }
        coVerify(exactly = 1) { retryQueue.queueFailedOperation(uploadType = "MyTeam", error = any(), payload = any(), endpoint = "teams", httpMethod = "POST", dbId = "team1", modelClassName = "MyTeam") }
    }

    @Test
    fun `uploadTeams handles bulk exception`() = runTest(testDispatcher) {
        val mockRepo = mockk<TeamsSyncRepository>(relaxed = true)
        every { teamsSyncRepository.get() } returns mockRepo

        val mockTeam = TeamUploadData("team1", JsonObject(), false, null)
        coEvery { mockRepo.getTeamsForUpload() } returns listOf(mockTeam)

        coEvery { uploadRepository.postUploadArray(any(), any()) } throws java.io.IOException("Network down")
        coEvery { retryQueue.queueFailedOperation(any(), any(), any(), any(), any(), any(), any()) } returns Unit

        teamsUploader.uploadTeams()
        advanceUntilIdle()

        coVerify(exactly = 1) { uploadRepository.postUploadArray("http://mock.url/teams/_bulk_docs", any()) }
        coVerify(exactly = 1) { retryQueue.queueFailedOperation(uploadType = "MyTeam", error = any(), payload = any(), endpoint = "teams", httpMethod = "POST", dbId = "team1", modelClassName = "MyTeam") }
    }

    @Test
    fun `uploadTeamImageAttachment propagates CancellationException`() = runTest(testDispatcher) {
        val mockTeam = TeamUploadData("team1", JsonObject(), false, "image.png")
        val mockRepo = mockk<TeamsSyncRepository>(relaxed = true)
        every { teamsSyncRepository.get() } returns mockRepo
        coEvery { mockRepo.getTeamsForUpload() } returns listOf(mockTeam)

        val bulkResponse = com.google.gson.JsonArray().apply {
            add(JsonObject().apply { addProperty("id", "team1"); addProperty("rev", "rev1") })
        }
        coEvery { uploadRepository.postUploadArray(any(), any()) } returns retrofit2.Response.success(bulkResponse)

        io.mockk.mockkObject(MyTeam)
        val mockFile = mockk<File>()
        every { MyTeam.getAttachmentFile(context, "team1", "image.png") } returns mockFile
        every { mockFile.exists() } returns true

        val cancelExc = CancellationException("Upload cancelled")
        coEvery { uploadRepository.uploadResource(any(), any(), any()) } throws cancelExc

        var caught: Exception? = null
        try {
            teamsUploader.uploadTeams()
        } catch (e: Exception) {
            caught = e
        } finally {
            io.mockk.unmockkObject(MyTeam)
        }

        assertTrue("Expected CancellationException, got $caught", caught is CancellationException || (caught != null && caught.cause is CancellationException))
        coVerify(exactly = 0) { mockRepo.markTeamsUploaded(any()) }
    }

    @Test
    fun `uploadTeamImageAttachment falls back to old rev on ordinary exception`() = runTest(testDispatcher) {
        val mockTeam = TeamUploadData("team1", JsonObject(), false, "image.png")
        val mockRepo = mockk<TeamsSyncRepository>(relaxed = true)
        every { teamsSyncRepository.get() } returns mockRepo
        coEvery { mockRepo.getTeamsForUpload() } returns listOf(mockTeam)

        val bulkResponse = com.google.gson.JsonArray().apply {
            add(JsonObject().apply { addProperty("id", "team1"); addProperty("rev", "rev1") })
        }
        coEvery { uploadRepository.postUploadArray(any(), any()) } returns retrofit2.Response.success(bulkResponse)

        io.mockk.mockkObject(MyTeam)
        val mockFile = mockk<File>()
        every { MyTeam.getAttachmentFile(context, "team1", "image.png") } returns mockFile
        every { mockFile.exists() } returns true

        coEvery { uploadRepository.uploadResource(any(), any(), any()) } throws IOException("Attachment upload failed")
        coEvery { mockRepo.markTeamsUploaded(any()) } returns Unit

        teamsUploader.uploadTeams()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepo.markTeamsUploaded(mapOf("team1" to "rev1")) }

        io.mockk.unmockkObject(MyTeam)
    }
}
