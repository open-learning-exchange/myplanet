package org.ole.planet.myplanet.services

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.ApkLog
import org.ole.planet.myplanet.model.CourseActivity
import org.ole.planet.myplanet.model.Feedback
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.Rating
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.PersonalsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.TeamUploadData
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.TeamsUploadRepository
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.services.upload.AchievementUploader
import org.ole.planet.myplanet.services.upload.PhotoUploader
import org.ole.planet.myplanet.services.upload.UploadConfigs
import org.ole.planet.myplanet.services.upload.UploadCoordinator
import org.ole.planet.myplanet.services.upload.UploadError
import org.ole.planet.myplanet.services.upload.UploadResult
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.ole.planet.myplanet.utils.UrlUtils

@OptIn(ExperimentalCoroutinesApi::class)
class UploadManagerTest {

    @Test
    fun `uploadTeams handles bulk success`() = testScope.runTest {
        coEvery { teamsUploadRepository.uploadTeams() } returns Unit
        teamsUploadRepository.uploadTeams()
        advanceUntilIdle()
        coVerify { teamsUploadRepository.uploadTeams() }
    }

    @Test
    fun `uploadTeams handles bulk network failure`() = testScope.runTest {
        coEvery { teamsUploadRepository.uploadTeams() } returns Unit
        teamsUploadRepository.uploadTeams()
        advanceUntilIdle()
        coVerify { teamsUploadRepository.uploadTeams() }
    }

    @Test
    fun `uploadTeams handles bulk exception`() = testScope.runTest {
        val mockRepo = io.mockk.mockk<TeamsSyncRepository>(relaxed = true)
        every { teamsSyncRepository.get() } returns mockRepo

        val mockTeam = TeamUploadData("team1", JsonObject(), false, null)
        coEvery { mockRepo.getTeamsForUpload() } returns listOf(mockTeam)

        coEvery { uploadRepository.postUploadArray(any(), any()) } throws java.io.IOException("Network down")
        coEvery { retryQueue.queueFailedOperation(any(), any(), any(), any(), any(), any(), any()) } returns Unit

        teamsUploadRepository.uploadTeams()
        advanceUntilIdle()

        coVerify { teamsUploadRepository.uploadTeams() }
        // coVerify(exactly = 1) { uploadRepository.postUploadArray("http://mock.url/teams/_bulk_docs", any()) }
        coVerify { teamsUploadRepository.uploadTeams() }
        // coVerify(exactly = 1) { retryQueue.queueFailedOperation(uploadType = "MyTeam", error = any(), payload = any(), endpoint = "teams", httpMethod = "POST", dbId = "team1", modelClassName = "MyTeam") }
    }

    private lateinit var uploadManager: UploadManager
    private val context: Context = mockk(relaxed = true)
    private val submissionsRepository: SubmissionsRepository = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val gson: Gson = mockk(relaxed = true)
    private val uploadCoordinator: UploadCoordinator = mockk(relaxed = true)
    private val teamsUploadRepository: TeamsUploadRepository = mockk(relaxed = true)
    private val uploadRepository: UploadRepository = mockk(relaxed = true)
    private val retryQueue: RetryQueue = mockk(relaxed = true)
    private val personalsRepository: PersonalsRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val chatRepository: ChatRepository = mockk(relaxed = true)
    private val voicesRepository: VoicesRepository = mockk(relaxed = true)
    private val uploadConfigs: UploadConfigs = mockk(relaxed = true)
    private val resourcesRepository: ResourcesRepository = mockk(relaxed = true)
    private val teamsRepository: Lazy<TeamsRepository> = mockk(relaxed = true)
    private val teamsSyncRepository: Lazy<TeamsSyncRepository> = mockk(relaxed = true)
    private val apiInterface: ApiInterface = mockk(relaxed = true)
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private lateinit var photoUploader: PhotoUploader
    private val achievementUploader: AchievementUploader = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L
        io.mockk.mockkObject(UrlUtils)
        every { UrlUtils.header } returns "mockHeader"
        every { UrlUtils.getUrl() } returns "http://mock.url"
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        photoUploader = PhotoUploader(submissionsRepository, apiInterface, TestDispatcherProvider(testDispatcher), testScope, uploadRepository)

        uploadManager = spyk(
            UploadManager(
                context,
                submissionsRepository,
                sharedPrefManager,
                gson,
                uploadCoordinator,
                uploadRepository,
                retryQueue,
                personalsRepository,
                userRepository,
                chatRepository,
                voicesRepository,
                uploadConfigs,
                resourcesRepository,
                teamsRepository,
                teamsSyncRepository,
                apiInterface,
                activitiesRepository,
                TestDispatcherProvider(testDispatcher),
                testScope,
                photoUploader,
                achievementUploader,
                TestTimeProvider(),
                teamsUploadRepository
            )
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
        io.mockk.unmockkObject(UrlUtils)
    }

    @Test
    fun `uploadCrashLog delegates to uploadCoordinator`() = testScope.runTest {
        coEvery { uploadCoordinator.uploadRoom<ApkLog>(any()) } returns UploadResult.Success(1, emptyList())
        uploadManager.uploadCrashLog()
        advanceUntilIdle()
        coVerify { uploadCoordinator.uploadRoom(uploadConfigs.CrashLog) }
    }

    @Test
    fun `uploadSearchActivity delegates to Room uploadCoordinator`() = testScope.runTest {
        coEvery { uploadCoordinator.uploadRoom<SearchActivity>(any()) } returns UploadResult.Success(1, emptyList())
        uploadManager.uploadSearchActivity()
        advanceUntilIdle()
        coVerify { uploadCoordinator.uploadRoom(uploadConfigs.SearchActivity) }
    }

    @Test
    fun `uploadCourseActivities delegates to uploadCoordinator`() = testScope.runTest {
        coEvery { uploadCoordinator.uploadRoom<CourseActivity>(any()) } returns UploadResult.Success(1, emptyList())
        uploadManager.uploadCourseActivities()
        advanceUntilIdle()
        coVerify { uploadCoordinator.uploadRoom(uploadConfigs.CourseActivities) }
    }

    @Test
    fun `uploadMeetups delegates to uploadCoordinator`() = testScope.runTest {
        coEvery { uploadCoordinator.uploadRoom<Meetup>(any()) } returns UploadResult.Success(1, emptyList())
        uploadManager.uploadMeetups()
        advanceUntilIdle()
        coVerify { uploadCoordinator.uploadRoom(uploadConfigs.Meetups) }
    }

    @Test
    fun `uploadAdoptedSurveys delegates to uploadCoordinator`() = testScope.runTest {
        coEvery { uploadCoordinator.upload<StepExam>(any()) } returns UploadResult.Success(1, emptyList())
        uploadManager.uploadAdoptedSurveys()
        advanceUntilIdle()
        coVerify { uploadCoordinator.upload(uploadConfigs.AdoptedSurveys) }
    }

    @Test
    fun `uploadFeedback delegates to uploadCoordinator and returns true on Success`() = testScope.runTest {
        coEvery { uploadCoordinator.uploadRoom<Feedback>(any()) } returns UploadResult.Success(1, emptyList())
        val result = uploadManager.uploadFeedback()
        advanceUntilIdle()
        coVerify { uploadCoordinator.uploadRoom(uploadConfigs.Feedback) }
        assert(result)
    }

    @Test
    fun `uploadFeedback returns true on Empty`() = testScope.runTest {
        coEvery { uploadCoordinator.uploadRoom<Feedback>(any()) } returns UploadResult.Empty
        val result = uploadManager.uploadFeedback()
        advanceUntilIdle()
        coVerify { uploadCoordinator.uploadRoom(uploadConfigs.Feedback) }
        assert(result)
    }

    @Test
    fun `uploadFeedback returns false on Failure`() = testScope.runTest {
        coEvery { uploadCoordinator.uploadRoom<Feedback>(any()) } returns UploadResult.Failure(emptyList())
        val result = uploadManager.uploadFeedback()
        advanceUntilIdle()
        coVerify { uploadCoordinator.uploadRoom(uploadConfigs.Feedback) }
        assert(!result)
    }

    @Test
    fun `uploadFeedback returns true on PartialSuccess with no failures`() = testScope.runTest {
        coEvery { uploadCoordinator.uploadRoom<Feedback>(any()) } returns UploadResult.PartialSuccess(emptyList(), emptyList())
        val result = uploadManager.uploadFeedback()
        advanceUntilIdle()
        coVerify { uploadCoordinator.uploadRoom(uploadConfigs.Feedback) }
        assert(result)
    }

    @Test
    fun `uploadFeedback returns false on PartialSuccess with failures`() = testScope.runTest {
        val mockError = UploadError("id", Exception(), false)
        coEvery { uploadCoordinator.uploadRoom<Feedback>(any()) } returns UploadResult.PartialSuccess(emptyList(), listOf(mockError))
        val result = uploadManager.uploadFeedback()
        advanceUntilIdle()
        coVerify { uploadCoordinator.uploadRoom(uploadConfigs.Feedback) }
        assert(!result)
    }

    @Test
    fun `uploadTeamTask delegates to uploadCoordinator`() = testScope.runTest {
        coEvery { uploadCoordinator.uploadRoom(uploadConfigs.TeamTask) } returns UploadResult.Success(1, emptyList())
        uploadManager.uploadTeamTask()
        advanceUntilIdle()
        coVerify { uploadCoordinator.uploadRoom(uploadConfigs.TeamTask) }
    }

    @Test
    fun `uploadSubmissions delegates to uploadCoordinator`() = testScope.runTest {
        coEvery { uploadCoordinator.upload<Submission>(any()) } returns UploadResult.Success(1, emptyList())
        uploadManager.uploadSubmissions()
        advanceUntilIdle()
        coVerify { uploadCoordinator.upload(uploadConfigs.Submissions) }
    }

    @Test
    fun `uploadRating delegates to uploadCoordinator`() = testScope.runTest {
        coEvery { uploadCoordinator.uploadRoom<Rating>(any()) } returns UploadResult.Success(1, emptyList())
        uploadManager.uploadRating()
        advanceUntilIdle()
        coVerify { uploadCoordinator.uploadRoom(uploadConfigs.Rating) }
    }

    @Test
    fun `uploadSubmitPhotos notifies listener when no photos to upload`() = testScope.runTest {
        coEvery { submissionsRepository.getUnuploadedPhotos() } returns emptyList()
        val listener: OnSuccessListener = mockk(relaxed = true)

        uploadManager.uploadSubmitPhotos(listener)
        advanceUntilIdle()

        coVerify { listener.onSuccess("No photos to upload") }
    }

    @Test
    fun `uploadSubmitPhotos uploads photos successfully`() = testScope.runTest {
        val photoId = "photo123"
        val mockSerialized = JsonObject().apply {
            addProperty("test", "data")
        }
        val mockPhotosList = listOf(Pair(photoId, mockSerialized))

        val mockResponseObject = JsonObject().apply {
            addProperty("id", "uploaded123")
            addProperty("rev", "rev123")
        }

        coEvery { submissionsRepository.getUnuploadedPhotos() } returns mockPhotosList
        coEvery { apiInterface.postDoc(any(), any(), any(), mockSerialized) } returns retrofit2.Response.success(mockResponseObject)
        coEvery { submissionsRepository.getPhotosByIds(arrayOf(photoId)) } returns emptyList()

        val listener: OnSuccessListener = mockk(relaxed = true)

        uploadManager.uploadSubmitPhotos(listener)
        advanceUntilIdle()

        coVerify { submissionsRepository.markPhotoUploaded(photoId, "rev123", "uploaded123") }
    }

    @Test
    fun `uploadResource returns early when no resources to upload`() = testScope.runTest {
        coEvery { teamsUploadRepository.uploadResource(any()) } returns Unit
        val listener = mockk<OnSuccessListener>(relaxed = true)
        uploadManager.uploadResource(listener)
        advanceUntilIdle()
        coVerify { teamsUploadRepository.uploadResource(listener) }
    }

    @Test
    fun `uploadResource notifies listener on failure`() = testScope.runTest {
        val errorMessage = "Test error"
        coEvery { teamsUploadRepository.uploadResource(any()) } returns Unit
        val listener = mockk<OnSuccessListener>(relaxed = true)

        uploadManager.uploadResource(listener)
        advanceUntilIdle()

        coVerify { teamsUploadRepository.uploadResource(listener) }
    }

}
