package org.ole.planet.myplanet.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
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
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.RealmApkLog
import org.ole.planet.myplanet.model.RealmCourseActivity
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.PersonalsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.upload.UploadConfigs
import org.ole.planet.myplanet.services.upload.UploadCoordinator
import org.ole.planet.myplanet.services.upload.UploadResult
import org.ole.planet.myplanet.services.upload.PhotoUploader
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class UploadManagerTest {
    private lateinit var uploadManager: UploadManager
    private val context: Context = mockk(relaxed = true)
    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val submissionsRepository: SubmissionsRepository = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val gson: Gson = mockk(relaxed = true)
    private val uploadCoordinator: UploadCoordinator = mockk(relaxed = true)
    private val personalsRepository: PersonalsRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val chatRepository: ChatRepository = mockk(relaxed = true)
    private val voicesRepository: VoicesRepository = mockk(relaxed = true)
    private val uploadConfigs: UploadConfigs = mockk(relaxed = true)
    private val resourcesRepository: ResourcesRepository = mockk(relaxed = true)
    private val teamsRepository: Lazy<TeamsRepository> = mockk(relaxed = true)
    private val apiInterface: ApiInterface = mockk(relaxed = true)
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private lateinit var photoUploader: PhotoUploader

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        io.mockk.mockkObject(org.ole.planet.myplanet.utils.UrlUtils)
        every { org.ole.planet.myplanet.utils.UrlUtils.header } returns "mockHeader"
        every { org.ole.planet.myplanet.utils.UrlUtils.getUrl() } returns "http://mock.url"
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        photoUploader = PhotoUploader(submissionsRepository, apiInterface, TestDispatcherProvider(testDispatcher), testScope)

        uploadManager = spyk(
            UploadManager(
                context,
                databaseService,
                submissionsRepository,
                sharedPrefManager,
                gson,
                uploadCoordinator,
                personalsRepository,
                userRepository,
                chatRepository,
                voicesRepository,
                uploadConfigs,
                resourcesRepository,
                teamsRepository,
                apiInterface,
                activitiesRepository,
                TestDispatcherProvider(testDispatcher),
                testScope,
                photoUploader
            )
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
        io.mockk.unmockkObject(org.ole.planet.myplanet.utils.UrlUtils)
    }

    @Test
    fun `uploadCrashLog delegates to uploadCoordinator`() = testScope.runTest {
        coEvery { uploadCoordinator.upload<RealmApkLog>(any()) } returns UploadResult.Success(1, emptyList())
        uploadManager.uploadCrashLog()
        advanceUntilIdle()
        coVerify { uploadCoordinator.upload(uploadConfigs.CrashLog) }
    }

    @Test
    fun `uploadCourseActivities delegates to uploadCoordinator`() = testScope.runTest {
        coEvery { uploadCoordinator.upload<RealmCourseActivity>(any()) } returns UploadResult.Success(1, emptyList())
        uploadManager.uploadCourseActivities()
        advanceUntilIdle()
        coVerify { uploadCoordinator.upload(uploadConfigs.CourseActivities) }
    }

    @Test
    fun `uploadMeetups delegates to uploadCoordinator`() = testScope.runTest {
        coEvery { uploadCoordinator.upload<RealmMeetup>(any()) } returns UploadResult.Success(1, emptyList())
        uploadManager.uploadMeetups()
        advanceUntilIdle()
        coVerify { uploadCoordinator.upload(uploadConfigs.Meetups) }
    }

    @Test
    fun `uploadAdoptedSurveys delegates to uploadCoordinator`() = testScope.runTest {
        coEvery { uploadCoordinator.upload<RealmStepExam>(any()) } returns UploadResult.Success(1, emptyList())
        uploadManager.uploadAdoptedSurveys()
        advanceUntilIdle()
        coVerify { uploadCoordinator.upload(uploadConfigs.AdoptedSurveys) }
    }

    @Test
    fun `uploadFeedback delegates to uploadCoordinator and returns true on Success`() = testScope.runTest {
        coEvery { uploadCoordinator.upload<RealmFeedback>(any()) } returns UploadResult.Success(1, emptyList())
        val result = uploadManager.uploadFeedback()
        advanceUntilIdle()
        coVerify { uploadCoordinator.upload(uploadConfigs.Feedback) }
        assert(result)
    }

    @Test
    fun `uploadFeedback returns true on Empty`() = testScope.runTest {
        coEvery { uploadCoordinator.upload<RealmFeedback>(any()) } returns org.ole.planet.myplanet.services.upload.UploadResult.Empty
        val result = uploadManager.uploadFeedback()
        advanceUntilIdle()
        coVerify { uploadCoordinator.upload(uploadConfigs.Feedback) }
        assert(result)
    }

    @Test
    fun `uploadFeedback returns false on Failure`() = testScope.runTest {
        coEvery { uploadCoordinator.upload<RealmFeedback>(any()) } returns org.ole.planet.myplanet.services.upload.UploadResult.Failure(emptyList())
        val result = uploadManager.uploadFeedback()
        advanceUntilIdle()
        coVerify { uploadCoordinator.upload(uploadConfigs.Feedback) }
        assert(!result)
    }

    @Test
    fun `uploadFeedback returns true on PartialSuccess with no failures`() = testScope.runTest {
        coEvery { uploadCoordinator.upload<RealmFeedback>(any()) } returns org.ole.planet.myplanet.services.upload.UploadResult.PartialSuccess(emptyList(), emptyList())
        val result = uploadManager.uploadFeedback()
        advanceUntilIdle()
        coVerify { uploadCoordinator.upload(uploadConfigs.Feedback) }
        assert(result)
    }

    @Test
    fun `uploadFeedback returns false on PartialSuccess with failures`() = testScope.runTest {
        val mockError = org.ole.planet.myplanet.services.upload.UploadError("id", Exception(), false)
        coEvery { uploadCoordinator.upload<RealmFeedback>(any()) } returns org.ole.planet.myplanet.services.upload.UploadResult.PartialSuccess(emptyList(), listOf(mockError))
        val result = uploadManager.uploadFeedback()
        advanceUntilIdle()
        coVerify { uploadCoordinator.upload(uploadConfigs.Feedback) }
        assert(!result)
    }

    @Test
    fun `uploadSubmissions delegates to uploadCoordinator`() = testScope.runTest {
        coEvery { uploadCoordinator.upload<RealmSubmission>(any()) } returns UploadResult.Success(1, emptyList())
        uploadManager.uploadSubmissions()
        advanceUntilIdle()
        coVerify { uploadCoordinator.upload(uploadConfigs.Submissions) }
    }

    @Test
    fun `uploadRating delegates to uploadCoordinator`() = testScope.runTest {
        coEvery { uploadCoordinator.upload<RealmRating>(any()) } returns UploadResult.Success(1, emptyList())
        uploadManager.uploadRating()
        advanceUntilIdle()
        coVerify { uploadCoordinator.upload(uploadConfigs.Rating) }
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
        val mockSerialized = com.google.gson.JsonObject().apply {
            addProperty("test", "data")
        }
        val mockPhotosList = listOf(Pair(photoId, mockSerialized))

        val mockResponseObject = com.google.gson.JsonObject().apply {
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
        coEvery { userRepository.getUserModelSuspending() } returns null
        coEvery { resourcesRepository.getUnuploadedResources(any()) } returns emptyList()
        val listener = mockk<OnSuccessListener>(relaxed = true)

        uploadManager.uploadResource(listener)
        advanceUntilIdle()

        coVerify { listener.onSuccess("No resources to upload") }
    }

    @Test
    fun `uploadResource notifies listener on failure`() = testScope.runTest {
        val errorMessage = "Test error"
        coEvery { userRepository.getUserModelSuspending() } throws Exception(errorMessage)
        val listener = mockk<OnSuccessListener>(relaxed = true)

        uploadManager.uploadResource(listener)
        advanceUntilIdle()

        coVerify { listener.onSuccess("Resource upload failed: $errorMessage") }
    }
}
