package org.ole.planet.myplanet.services.sync

import android.content.Context
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ChatSyncWriter
import org.ole.planet.myplanet.repository.CommunitySyncWriter
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.FeedbackSyncWriter
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TagsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
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
    private val communityRepository: CommunitySyncWriter = mockk()
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
            communityRepository,
            healthRepository,
            progressRepository,
            surveysRepository,
            dispatcherProvider,
            userSessionManager
        )
    }

    @After
    fun tearDown() {
        unmockkObject(UrlUtils)
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
