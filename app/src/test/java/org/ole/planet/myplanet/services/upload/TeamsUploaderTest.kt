package org.ole.planet.myplanet.services.upload

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.repository.TeamUploadData
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

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
        mockkStatic(Uri::class)
        every { Uri.encode(any()) } answers { firstArg() }
        mockkObject(FileUtils)
        every { FileUtils.getMimeType(any()) } returns "image/png"
        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "http://mock.url"
        every { UrlUtils.header } returns "Basic mock-header"
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
        unmockkObject(UrlUtils)
    }

    @Test
    fun `uploadTeams handles bulk success`() = runTest(testDispatcher) {
        val mockTeam1 = TeamUploadData("team1", JsonObject(), false, null)
        val mockTeam2 = TeamUploadData("team2", JsonObject(), false, null)
        val mockTeam3 = TeamUploadData("team3", JsonObject(), true, null)
        val mockRepo = mockk<TeamsSyncRepository>(relaxed = true)
        every { teamsSyncRepository.get() } returns mockRepo
        coEvery { mockRepo.getTeamsForUpload() } returns listOf(mockTeam1, mockTeam2, mockTeam3)

        val bulkResponse = JsonArray().apply {
            add(JsonObject().apply { addProperty("id", "team1"); addProperty("rev", "rev1") })
            add(JsonObject().apply { addProperty("id", "team2"); addProperty("error", "conflict") })
            add(JsonObject().apply { addProperty("id", "team3"); addProperty("rev", "rev3") })
        }
        coEvery { uploadRepository.postUploadArray(any(), any()) } returns Response.success(bulkResponse)

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

        val bulkResponse = JsonArray().apply {
            add(JsonObject().apply { addProperty("id", "serverGeneratedId1"); addProperty("rev", "rev1") })
        }
        coEvery { uploadRepository.postUploadArray(any(), any()) } returns Response.success(bulkResponse)
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
        coEvery { uploadRepository.postUploadArray(any(), any()) } returns Response.error(500, errorBody)
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

        coEvery { uploadRepository.postUploadArray(any(), any()) } throws IOException("Network down")
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

        val bulkResponse = JsonArray().apply {
            add(JsonObject().apply { addProperty("id", "team1"); addProperty("rev", "rev1") })
        }
        coEvery { uploadRepository.postUploadArray(any(), any()) } returns Response.success(bulkResponse)

        mockkObject(MyTeam)
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
            unmockkObject(MyTeam)
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

        val bulkResponse = JsonArray().apply {
            add(JsonObject().apply { addProperty("id", "team1"); addProperty("rev", "rev1") })
        }
        coEvery { uploadRepository.postUploadArray(any(), any()) } returns Response.success(bulkResponse)

        mockkObject(MyTeam)
        val mockFile = mockk<File>()
        every { MyTeam.getAttachmentFile(context, "team1", "image.png") } returns mockFile
        every { mockFile.exists() } returns true

        coEvery { uploadRepository.uploadResource(any(), any(), any()) } throws IOException("Attachment upload failed")
        coEvery { mockRepo.markTeamsUploaded(any()) } returns Unit

        teamsUploader.uploadTeams()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepo.markTeamsUploaded(mapOf("team1" to "rev1")) }

        unmockkObject(MyTeam)
    }

    @Test
    fun `server-only cover is kept with an attachment stub`() = runTest(testDispatcher) {
        val serialized = JsonObject().apply { addProperty("_id", "team1"); addProperty("_rev", "1-a") }
        val team = TeamUploadData("team1", serialized, false, "cover.webp")
        val mockRepo = mockk<TeamsSyncRepository>(relaxed = true)
        every { teamsSyncRepository.get() } returns mockRepo
        coEvery { mockRepo.getTeamsForUpload() } returns listOf(team)

        mockkObject(MyTeam)
        val missingFile = mockk<File>()
        every { MyTeam.getAttachmentFile(context, "team1", "cover.webp") } returns missingFile
        every { missingFile.exists() } returns false

        val body = slot<JsonObject>()
        coEvery { uploadRepository.postUploadArray(any(), capture(body)) } returns Response.success(JsonArray())

        teamsUploader.uploadTeams()
        advanceUntilIdle()
        unmockkObject(MyTeam)

        val doc = body.captured.getAsJsonArray("docs")[0].asJsonObject
        assertTrue(doc.getAsJsonObject("_attachments").getAsJsonObject("cover.webp").get("stub").asBoolean)
        assertFalse("stub must not leak into the stored payload", serialized.has("_attachments"))
        coVerify(exactly = 0) { uploadRepository.uploadResource(any(), any(), any()) }
    }

    @Test
    fun `locally picked cover is re-uploaded instead of stubbed`() = runTest(testDispatcher) {
        val serialized = JsonObject().apply { addProperty("_id", "team1"); addProperty("_rev", "1-a") }
        val team = TeamUploadData("team1", serialized, false, "cover.webp")
        val mockRepo = mockk<TeamsSyncRepository>(relaxed = true)
        every { teamsSyncRepository.get() } returns mockRepo
        coEvery { mockRepo.getTeamsForUpload() } returns listOf(team)

        mockkObject(MyTeam)
        val localFile = File.createTempFile("cover", ".webp").apply { deleteOnExit() }
        every { MyTeam.getAttachmentFile(context, "team1", "cover.webp") } returns localFile

        val body = slot<JsonObject>()
        val bulkResponse = JsonArray().apply {
            add(JsonObject().apply { addProperty("id", "team1"); addProperty("rev", "2-b") })
        }
        coEvery { uploadRepository.postUploadArray(any(), capture(body)) } returns Response.success(bulkResponse)
        coEvery { uploadRepository.uploadResource(any(), any(), any()) } returns
            Response.success(JsonObject().apply { addProperty("rev", "3-c") })

        teamsUploader.uploadTeams()
        advanceUntilIdle()
        unmockkObject(MyTeam)

        val doc = body.captured.getAsJsonArray("docs")[0].asJsonObject
        assertFalse(doc.has("_attachments"))
        coVerify(exactly = 1) { uploadRepository.uploadResource(any(), "http://mock.url/teams/team1/cover.webp", any()) }
        coVerify(exactly = 1) { mockRepo.markTeamsUploaded(mapOf("team1" to "3-c")) }
    }
}
