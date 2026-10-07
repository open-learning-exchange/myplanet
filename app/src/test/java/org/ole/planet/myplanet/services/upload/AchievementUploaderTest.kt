package org.ole.planet.myplanet.services.upload

import android.content.Context
import android.util.Log
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
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
        val achievement1 = buildJsonObject { put("_id", "ach1") }
        val achievement2 = buildJsonObject { put("_id", "ach2") }
        coEvery { userAchievementsRepository.getAchievementsForUpload() } returns listOf(achievement1, achievement2)

        coEvery { uploadRepository.putJson("http://mock.url/achievements/ach1", achievement1) } throws CancellationException("Sync cancelled")

        var caughtCancellation = false
        try {
            achievementUploader.uploadAchievement()
        } catch (e: CancellationException) {
            caughtCancellation = true
        }

        assertTrue("Expected CancellationException to be caught", caughtCancellation)
        coVerify(exactly = 1) { uploadRepository.putJson("http://mock.url/achievements/ach1", achievement1) }
        coVerify(exactly = 0) { uploadRepository.putJson("http://mock.url/achievements/ach2", achievement2) }
    }

    @Test
    fun `uploadAchievement catches ordinary exception and continues loop`() = runTest(testDispatcher) {
        val achievement1 = buildJsonObject { put("_id", "ach1") }
        val achievement2 = buildJsonObject { put("_id", "ach2") }
        coEvery { userAchievementsRepository.getAchievementsForUpload() } returns listOf(achievement1, achievement2)

        coEvery { uploadRepository.putJson("http://mock.url/achievements/ach1", achievement1) } throws IOException("Network error")
        val successResponse = Response.success(buildJsonObject { put("rev", "rev2") })
        coEvery { uploadRepository.putJson("http://mock.url/achievements/ach2", achievement2) } returns successResponse

        achievementUploader.uploadAchievement()

        coVerify(exactly = 1) { uploadRepository.putJson("http://mock.url/achievements/ach1", achievement1) }
        coVerify(exactly = 1) { uploadRepository.putJson("http://mock.url/achievements/ach2", achievement2) }
        coVerify(exactly = 1) { userAchievementsRepository.markAchievementUploaded("ach2", "rev2") }
    }

    @Test
    fun `uploadCvAttachment propagates CancellationException`() = runTest(testDispatcher) {
        val achievement = buildJsonObject {
            put("_id", "ach1")
            put("resumeFileName", "resume.pdf")
        }
        coEvery { userAchievementsRepository.getAchievementsForUpload() } returns listOf(achievement)

        val successResponse = Response.success(buildJsonObject { put("rev", "rev1") })
        coEvery { uploadRepository.putJson("http://mock.url/achievements/ach1", achievement) } returns successResponse

        val cvFolder = temporaryFolder.newFolder("cv")
        val resumeFile = File(cvFolder, "resume.pdf")
        resumeFile.createNewFile()

        every { FileUtils.getOlePath(context) } returns temporaryFolder.root.absolutePath + "/"

        coEvery { uploadRepository.uploadResourceJson(any(), any(), any()) } throws CancellationException("Cancelled")

        var caughtCancellation = false
        try {
            achievementUploader.uploadAchievement()
        } catch (e: CancellationException) {
            caughtCancellation = true
        }

        assertTrue("Expected CancellationException to be caught", caughtCancellation)
    }
}
