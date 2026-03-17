package org.ole.planet.myplanet.services

import android.content.Context
import com.google.gson.Gson
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.PersonalsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.upload.UploadConfigs
import org.ole.planet.myplanet.services.upload.UploadCoordinator
import org.ole.planet.myplanet.services.upload.UploadResult
import org.ole.planet.myplanet.utils.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class UploadManagerTest {

    private lateinit var uploadManager: UploadManager
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockDatabaseService = mockk<DatabaseService>(relaxed = true)
    private val mockSubmissionsRepository = mockk<SubmissionsRepository>(relaxed = true)
    private val mockSharedPrefManager = mockk<SharedPrefManager>(relaxed = true)
    private val mockGson = mockk<Gson>(relaxed = true)
    private val mockUploadCoordinator = mockk<UploadCoordinator>(relaxed = true)
    private val mockPersonalsRepository = mockk<PersonalsRepository>(relaxed = true)
    private val mockUserRepository = mockk<UserRepository>(relaxed = true)
    private val mockChatRepository = mockk<ChatRepository>(relaxed = true)
    private val mockUploadConfigs = mockk<UploadConfigs>(relaxed = true)
    private val mockTeamsRepository = mockk<Lazy<TeamsRepository>>(relaxed = true)
    private val mockApiInterface = mockk<org.ole.planet.myplanet.data.api.ApiInterface>(relaxed = true)
    private val mockListener = mockk<OnSuccessListener>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        uploadManager = UploadManager(
            mockContext,
            mockDatabaseService,
            mockSubmissionsRepository,
            mockSharedPrefManager,
            mockGson,
            mockUploadCoordinator,
            mockPersonalsRepository,
            mockUserRepository,
            mockChatRepository,
            mockUploadConfigs,
            mockTeamsRepository,
            mockApiInterface,
            testDispatcherProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uploadExamResult completes successfully`() = runTest(testDispatcher) {
        val dummyConfig = mockk<org.ole.planet.myplanet.services.upload.UploadConfig<org.ole.planet.myplanet.model.RealmSubmission>>(relaxed = true)
        val dummyResult = UploadResult.Success(10, emptyList())

        coEvery { mockUploadConfigs.ExamResults } returns dummyConfig
        coEvery { mockUploadCoordinator.upload(any<org.ole.planet.myplanet.services.upload.UploadConfig<org.ole.planet.myplanet.model.RealmSubmission>>()) } returns dummyResult

        uploadManager.uploadExamResult(mockListener)

        advanceUntilIdle()

        coVerify { mockUploadCoordinator.upload(dummyConfig) }
        coVerify { mockListener.onSuccess("Result sync completed successfully (10 processed, 0 errors)") }
    }
}
