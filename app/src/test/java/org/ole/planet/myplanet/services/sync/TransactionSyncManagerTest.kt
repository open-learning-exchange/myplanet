package org.ole.planet.myplanet.services.sync

import android.content.Context
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
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionSyncManagerTest {

    private lateinit var transactionSyncManager: TransactionSyncManager
    private val apiInterface: ApiInterface = mockk()
    private val databaseService: DatabaseService = mockk()
    private val context: Context = mockk()
    private val voicesRepository: VoicesRepository = mockk()
    private val chatRepository: ChatRepository = mockk()
    private val feedbackRepository: FeedbackRepository = mockk()
    private val sharedPrefManager: SharedPrefManager = mockk()
    private val userRepository: UserRepository = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "http://mockurl"
        every { UrlUtils.header } returns "Basic mockHeader"

        transactionSyncManager = TransactionSyncManager(
            apiInterface,
            databaseService,
            context,
            voicesRepository,
            chatRepository,
            feedbackRepository,
            sharedPrefManager,
            userRepository,
            testScope
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
