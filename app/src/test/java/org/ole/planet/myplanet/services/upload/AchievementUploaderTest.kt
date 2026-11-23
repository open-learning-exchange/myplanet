package org.ole.planet.myplanet.services.upload

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UserAchievementsRepository
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementUploaderTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val userAchievementsRepository: UserAchievementsRepository = mockk(relaxed = true)
    private val uploadRepository: UploadRepository = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var achievementUploader: AchievementUploader

    @Before
    fun setup() {
        mockkStatic(Log::class)
        mockkObject(UrlUtils)
        mockkObject(FileUtils)

        every { UrlUtils.getUrl() } returns "http://mock.url"
        every { UrlUtils.header } returns "Basic mock-header"
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        achievementUploader = AchievementUploader(
            context,
            userAchievementsRepository,
            uploadRepository,
            TestDispatcherProvider(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `uploadAchievement propagates CancellationException and halts further uploads`() = runTest(testDispatcher) {
        val achievement1 = JsonObject().apply { addProperty("_id", "ach1") }
        val achievement2 = JsonObject().apply { addProperty("_id", "ach2") }
        coEvery { userAchievementsRepository.getAchievementsForUpload() } returns listOf(achievement1, achievement2)

        coEvery { uploadRepository.putUpload("http://mock.url/achievements/ach1", achievement1) } throws CancellationException("Sync cancelled")

        var caughtCancellation = false
        try {
            achievementUploader.uploadAchievement()
        } catch (e: CancellationException) {
            caughtCancellation = true
        }

        assertTrue("Expected CancellationException to be caught", caughtCancellation)
        coVerify(exactly = 1) { uploadRepository.putUpload("http://mock.url/achievements/ach1", achievement1) }
        coVerify(exactly = 0) { uploadRepository.putUpload("http://mock.url/achievements/ach2", achievement2) }
    }

    @Test
    fun `uploadAchievement catches ordinary exception and continues loop`() = runTest(testDispatcher) {
        val achievement1 = JsonObject().apply { addProperty("_id", "ach1") }
        val achievement2 = JsonObject().apply { addProperty("_id", "ach2") }
        coEvery { userAchievementsRepository.getAchievementsForUpload() } returns listOf(achievement1, achievement2)

        coEvery { uploadRepository.putUpload("http://mock.url/achievements/ach1", achievement1) } throws IOException("Network error")
        val successResponse = Response.success(JsonObject().apply { addProperty("rev", "rev2") })
        coEvery { uploadRepository.putUpload("http://mock.url/achievements/ach2", achievement2) } returns successResponse

        achievementUploader.uploadAchievement()

        coVerify(exactly = 1) { uploadRepository.putUpload("http://mock.url/achievements/ach1", achievement1) }
        coVerify(exactly = 1) { uploadRepository.putUpload("http://mock.url/achievements/ach2", achievement2) }
        coVerify(exactly = 1) { userAchievementsRepository.markAchievementUploaded("ach2", "rev2") }
    }

    @Test
    fun `uploadCvAttachment propagates CancellationException`() = runTest(testDispatcher) {
        val achievement = JsonObject().apply {
            addProperty("_id", "ach1")
            addProperty("resumeFileName", "resume.pdf")
        }
        coEvery { userAchievementsRepository.getAchievementsForUpload() } returns listOf(achievement)

        val successResponse = Response.success(JsonObject().apply { addProperty("rev", "rev1") })
        coEvery { uploadRepository.putUpload("http://mock.url/achievements/ach1", achievement) } returns successResponse

        val cvFolder = temporaryFolder.newFolder("cv")
        val resumeFile = File(cvFolder, "resume.pdf")
        resumeFile.createNewFile()

        every { FileUtils.getOlePath(context) } returns temporaryFolder.root.absolutePath + "/"

        coEvery { uploadRepository.uploadResource(any(), any(), any()) } throws CancellationException("Cancelled")

        var caughtCancellation = false
        try {
            achievementUploader.uploadAchievement()
        } catch (e: CancellationException) {
            caughtCancellation = true
        }

        assertTrue("Expected CancellationException to be caught", caughtCancellation)
    }

    @Test
    fun `uploadAchievement stores the rev from the CV upload`() = runTest(testDispatcher) {
        val achievement = JsonObject().apply {
            addProperty("_id", "ach1")
            addProperty("resumeFileName", "resume.pdf")
        }
        coEvery { userAchievementsRepository.getAchievementsForUpload() } returns listOf(achievement)
        coEvery { uploadRepository.putUpload("http://mock.url/achievements/ach1", achievement) } returns
            Response.success(JsonObject().apply { addProperty("rev", "1-doc") })
        val olePath = temporaryFolder.root.absolutePath + "/"
        every { FileUtils.getOlePath(context) } returns olePath
        File(olePath + org.ole.planet.myplanet.model.Achievement.resolveCvRelativePath(olePath, "ach1", "resume.pdf")).apply {
            parentFile?.mkdirs()
            createNewFile()
        }
        coEvery { uploadRepository.uploadResource(any(), any(), any()) } returns
            Response.success(JsonObject().apply { addProperty("rev", "2-cv") })

        achievementUploader.uploadAchievement()

        coVerify(exactly = 1) { userAchievementsRepository.markAchievementUploaded("ach1", "2-cv") }
        coVerify(exactly = 0) { userAchievementsRepository.markAchievementUploaded("ach1", "1-doc") }
    }

    @Test
    fun `uploadAchievement re-sends local content on top of the server rev after a 409`() = runTest(testDispatcher) {
        val achievement = JsonObject().apply {
            addProperty("_id", "ach1")
            addProperty("_rev", "1-stale")
            addProperty("purpose", "local edit")
        }
        val url = "http://mock.url/achievements/ach1"
        coEvery { userAchievementsRepository.getAchievementsForUpload() } returns listOf(achievement)
        val sent = mutableListOf<JsonObject>()
        coEvery { uploadRepository.putUpload(url, capture(sent)) } returnsMany listOf(
            Response.error(409, "".toResponseBody(null)),
            Response.success(JsonObject().apply { addProperty("rev", "4-rebased") })
        )
        coEvery { uploadRepository.fetchExistingDoc(url) } returns
            Response.success(JsonObject().apply { addProperty("_rev", "3-server") })

        achievementUploader.uploadAchievement()

        assertEquals("3-server", sent.last().get("_rev").asString)
        assertEquals("local edit", sent.last().get("purpose").asString)
        coVerify(exactly = 1) { userAchievementsRepository.markAchievementUploaded("ach1", "4-rebased") }
    }

    @Test
    fun `uploadAchievement leaves the row pending when the conflict retry fails`() = runTest(testDispatcher) {
        val achievement = JsonObject().apply { addProperty("_id", "ach1") }
        val url = "http://mock.url/achievements/ach1"
        coEvery { userAchievementsRepository.getAchievementsForUpload() } returns listOf(achievement)
        coEvery { uploadRepository.putUpload(url, any()) } returns Response.error(409, "".toResponseBody(null))
        coEvery { uploadRepository.fetchExistingDoc(url) } returns
            Response.success(JsonObject().apply { addProperty("_rev", "3-server") })

        achievementUploader.uploadAchievement()

        coVerify(exactly = 0) { userAchievementsRepository.markAchievementUploaded(any(), any()) }
    }
}
