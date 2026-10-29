package org.ole.planet.myplanet.services.sync

import android.content.Context
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.MediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.AppNotification
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ChatSyncWriter
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.EventsSyncWriter
import org.ole.planet.myplanet.repository.FeedbackSyncWriter
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TagsRepository
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.UserSyncRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionSyncManagerTest {

    private lateinit var transactionSyncManager: TransactionSyncManager
    private val apiInterface: ApiInterface = mockk()
    private val context: Context = mockk()
    private val voicesRepository: VoicesRepository = mockk()
    private val chatRepository: ChatSyncWriter = mockk()
    private val feedbackRepository: FeedbackSyncWriter = mockk()
    private val sharedPrefManager: SharedPrefManager = mockk()
    private val userRepository: UserRepository = mockk()
    private val userSyncRepository: UserSyncRepository = mockk()
    private val activitiesRepository: ActivitiesRepository = mockk()
    private val teamsSyncRepository: Lazy<TeamsSyncRepository> = mockk()
	private val notificationsRepository: NotificationsRepository = mockk()
    private val tagsRepository: TagsRepository = mockk()
    private val ratingsRepository: RatingsRepository = mockk()
    private val submissionsRepository: SubmissionsRepository = mockk()
    private val coursesRepository: CoursesRepository = mockk()
    private val eventsSyncWriter: EventsSyncWriter = mockk()
    private val healthRepository: HealthRepository = mockk()
    private val progressRepository: ProgressRepository = mockk()
    private val surveysRepository: SurveysRepository = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val dispatcherProvider: DispatcherProvider = mockk()
    private val userSessionManager: org.ole.planet.myplanet.services.UserSessionManager = mockk()

    @Before
    fun setup() {
        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "http://mockurl"
        every { UrlUtils.header } returns "Basic mockHeader"
        every { dispatcherProvider.io } returns testDispatcher
        every { dispatcherProvider.main } returns testDispatcher

        transactionSyncManager = TransactionSyncManager(
            apiInterface,
            context,
            voicesRepository,
            chatRepository,
            feedbackRepository,
            sharedPrefManager,
            userRepository,
            userSyncRepository,
            activitiesRepository,
            teamsSyncRepository,
			notificationsRepository,
            tagsRepository,
            ratingsRepository,
            submissionsRepository,
            coursesRepository,
            eventsSyncWriter,
            healthRepository,
            progressRepository,
            surveysRepository,
            dispatcherProvider,
            userSessionManager,
            mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        unmockkObject(UrlUtils)
    }

    @Test
    fun `syncNotificationReads keeps the server document's fields and only marks it read`() = testScope.runTest {
        val notification = AppNotification().apply { id = "n1"; rev = "1-a"; userId = "u1"; message = "local copy" }
        coEvery { notificationsRepository.getPendingSyncNotifications() } returns listOf(notification)
        coEvery { notificationsRepository.markNotificationsSynced(any()) } returns Unit
        val serverDoc = buildJsonObject {
            put("_id", "n1")
            put("_rev", "2-b")
            put("status", "unread")
            put("item", "team1")
            put("replyTo", "news1")
            put("time", 1000L)
            put("linkParams", buildJsonObject { put("activeTab", "applicantTab") })
        }
        coEvery { apiInterface.getJsonObject(any(), "http://mockurl/notifications/n1") } returns Response.success(serverDoc)
        val sent = slot<kotlinx.serialization.json.JsonObject>()
        coEvery { apiInterface.putDoc(any(), any(), "http://mockurl/notifications/n1", capture(sent)) } returns
            Response.success(buildJsonObject { put("rev", "3-c") })

        transactionSyncManager.syncNotificationReads()

        val body = sent.captured
        assertEquals("read", body["status"]?.jsonPrimitive?.content)
        assertEquals("2-b", body["_rev"]?.jsonPrimitive?.content)
        assertEquals("team1", body["item"]?.jsonPrimitive?.content)
        assertEquals("news1", body["replyTo"]?.jsonPrimitive?.content)
        assertEquals(1000L, body["time"]?.jsonPrimitive?.long)
        assertEquals("applicantTab", body["linkParams"]?.jsonObject?.get("activeTab")?.jsonPrimitive?.content)
        coVerify { notificationsRepository.markNotificationsSynced(listOf("n1" to "3-c")) }
    }

    @Test
    fun `downloadAttachment moves a complete download into place without leaving a part file`() = testScope.runTest {
        val dir = Files.createTempDirectory("attachments").toFile()
        val destFile = File(dir, "cover.png")
        val bytes = ByteArray(512) { it.toByte() }
        coEvery { apiInterface.downloadFile(any(), any()) } returns Response.success(bytes.toResponseBody(null))

        transactionSyncManager.downloadAttachment("http://mockurl/courses/c1/cover.png", destFile)

        assertTrue(destFile.readBytes().contentEquals(bytes))
        assertFalse(File(dir, "cover.png.part").exists())
        dir.deleteRecursively()
    }

    @Test
    fun `downloadAttachment leaves no file behind when the body is truncated`() = testScope.runTest {
        val dir = Files.createTempDirectory("attachments").toFile()
        val destFile = File(dir, "resume.pdf")
        val truncatedBody = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = 4096
            override fun source(): BufferedSource = Buffer().write(ByteArray(1024))
        }
        coEvery { apiInterface.downloadFile(any(), any()) } returns Response.success(truncatedBody)

        transactionSyncManager.downloadAttachment("http://mockurl/achievements/a1/resume.pdf", destFile)

        assertFalse(destFile.exists())
        assertFalse(File(dir, "resume.pdf.part").exists())
        dir.deleteRecursively()
    }

    @Test
    fun `downloadAttachment leaves no file behind when the download fails midway`() = testScope.runTest {
        val dir = Files.createTempDirectory("attachments").toFile()
        val destFile = File(dir, "team.png")
        val failingBody = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = -1
            override fun source(): BufferedSource = object : ForwardingSource(Buffer().write(ByteArray(1024))) {
                private var reads = 0
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (reads++ > 0) throw IOException("connection reset")
                    return super.read(sink, byteCount)
                }
            }.buffer()
        }
        coEvery { apiInterface.downloadFile(any(), any()) } returns Response.success(failingBody)

        transactionSyncManager.downloadAttachment("http://mockurl/teams/t1/team.png", destFile)

        assertFalse(destFile.exists())
        assertFalse(File(dir, "team.png.part").exists())
        dir.deleteRecursively()
    }

    @Test
    fun authenticate_success_returnsTrue() = testScope.runTest {
        val mockResponse = mockk<Response<DocumentResponse>>()
        every { mockResponse.code() } returns 200
        every { mockResponse.body() } returns mockk<DocumentResponse>()
        coEvery { apiInterface.getDocuments(any(), any()) } returns mockResponse

        val result = transactionSyncManager.authenticate()

        assertTrue(result)
    }

    @Test
    fun authenticate_failure_returnsFalse() = testScope.runTest {
        val mockResponse = mockk<Response<DocumentResponse>>()
        every { mockResponse.code() } returns 401
        coEvery { apiInterface.getDocuments(any(), any()) } returns mockResponse

        val result = transactionSyncManager.authenticate()

        assertFalse(result)
    }

    @Test
    fun authenticate_exception_returnsFalse() = testScope.runTest {
        coEvery { apiInterface.getDocuments(any(), any()) } throws Exception("Network Error")

        val result = transactionSyncManager.authenticate()

        assertFalse(result)
    }
}
