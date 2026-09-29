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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UserAchievementsRepository
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementUploaderTest {

    private val context: Context = mockk(relaxed = true)
    private val userAchievementsRepository: UserAchievementsRepository = mockk(relaxed = true)
    private val uploadRepository: UploadRepository = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var achievementUploader: AchievementUploader

    @Before
    fun setup() {
        mockkStatic(Log::class)
        mockkObject(UrlUtils)
        mockkObject(FileUtils)

        every { UrlUtils.getUrl() } returns "http://mock.url"
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
    fun `uploadAchievement propagates CancellationException and halts further uploads`() = testScope.runTest {
        val achievement1 = JsonObject().apply { addProperty("_id", "ach1") }
        val achievement2 = JsonObject().apply { addProperty("_id", "ach2") }
        coEvery { userAchievementsRepository.getAchievementsForUpload() } returns listOf(achievement1, achievement2)

        coEvery { uploadRepository.putUpload("http://mock.url/achievements/ach1", achievement1) } throws CancellationException("Sync cancelled")

        assertThrows(CancellationException::class.java) {
            achievementUploader.uploadAchievement()
        }

        coVerify(exactly = 1) { uploadRepository.putUpload("http://mock.url/achievements/ach1", achievement1) }
        coVerify(exactly = 0) { uploadRepository.putUpload("http://mock.url/achievements/ach2", achievement2) }
    }

    @Test
    fun `uploadAchievement catches ordinary exception and continues loop`() = testScope.runTest {
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
    fun `uploadCvAttachment propagates CancellationException`() = testScope.runTest {
        val achievement = JsonObject().apply {
            addProperty("_id", "ach1")
            addProperty("resumeFileName", "resume.pdf")
        }
        coEvery { userAchievementsRepository.getAchievementsForUpload() } returns listOf(achievement)

        val successResponse = Response.success(JsonObject().apply { addProperty("rev", "rev1") })
        coEvery { uploadRepository.putUpload("http://mock.url/achievements/ach1", achievement) } returns successResponse

        every { FileUtils.getOlePath(context) } returns "/mock/path/"
        val mockFile = mockk<File>()
        every { mockFile.exists() } returns true

        coEvery { uploadRepository.uploadResource(any(), any(), any()) } throws CancellationException("Cancelled")

        assertThrows(CancellationException::class.java) {
            achievementUploader.uploadAchievement()
        }
    }
}
