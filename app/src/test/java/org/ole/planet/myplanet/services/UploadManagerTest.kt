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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.RealmApkLog
import org.ole.planet.myplanet.model.RealmCourseActivity
import org.ole.planet.myplanet.model.RealmCourseProgress
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
import org.ole.planet.myplanet.services.upload.UploadConfig
import org.ole.planet.myplanet.services.upload.UploadConfigs
import org.ole.planet.myplanet.services.upload.UploadCoordinator
import org.ole.planet.myplanet.services.upload.UploadResult
import io.realm.RealmObject

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

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

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
                testScope
            )
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
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
    fun `uploadFeedback delegates to uploadCoordinator`() = testScope.runTest {
        coEvery { uploadCoordinator.upload<RealmFeedback>(any()) } returns UploadResult.Success(1, emptyList())
        val result = uploadManager.uploadFeedback()
        advanceUntilIdle()
        coVerify { uploadCoordinator.upload(uploadConfigs.Feedback) }
        assert(result)
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
}
