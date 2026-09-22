package org.ole.planet.myplanet.services.sync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SyncRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.UserSyncRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.Response
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.SyncTimeLogger
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class SyncManagerTest {

    private lateinit var syncManager: SyncManager
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val apiInterface: ApiInterface = mockk(relaxed = true)
    private val transactionSyncManager: TransactionSyncManager = mockk(relaxed = true)
    private val resourcesRepository: ResourcesRepository = mockk(relaxed = true)
    private val loginSyncManager: LoginSyncManager = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider(testDispatcher)
    private val listener: OnSyncListener = mockk(relaxed = true)
    private val userSyncRepository: UserSyncRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val syncRepository: SyncRepository = mockk(relaxed = true)
    private val syncTimeLogger: SyncTimeLogger = mockk(relaxed = true)
    private val userModel: UserEntity = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkObject(MainApplication.Companion)
        every { MainApplication.createLog(any(), any()) } returns Unit
        coEvery { userRepository.getUserModel() } returns userModel
        UrlUtils.init(sharedPrefManager)
        every { sharedPrefManager.getCouchdbUrl() } returns "http://localhost:5984/db"
        every { sharedPrefManager.isAlternativeUrl() } returns false
        every { sharedPrefManager.getUrlUser() } returns "admin"
        every { sharedPrefManager.getUrlPwd() } returns "password"

        syncManager = SyncManager(
            context = context,
            sharedPrefManager = sharedPrefManager,
            apiInterface = apiInterface,
            transactionSyncManager = transactionSyncManager,
            resourcesRepository = resourcesRepository,
            loginSyncManager = loginSyncManager,
            syncScope = testScope,
            activitiesRepository = activitiesRepository,
            dispatcherProvider = dispatcherProvider,
            timeProvider = TestTimeProvider(),
            userSyncRepository = userSyncRepository,
            userRepository = userRepository,
            syncRepository = syncRepository,
            syncTimeLogger = syncTimeLogger
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `resetSyncStatus sets status to Idle`() = runTest {
        // First change it to something else to ensure it actually resets
        syncManager.start(listener, "sync", listOf())
        syncManager.resetSyncStatus()
        assertEquals(SyncManager.SyncStatus.Idle, syncManager.syncStatus.value)
    }

    @Test
    fun `start authenticates and reports failure when authentication fails`() = runTest {
        coEvery { transactionSyncManager.authenticate() } returns false
        val expectedMessage = context.getString(org.ole.planet.myplanet.R.string.invalid_configuration)

        syncManager.start(listener, "sync", listOf("exams"))

        verify { listener.onSyncStarted() }
        coVerify { transactionSyncManager.authenticate() }
        verify { listener.onSyncFailed(expectedMessage) }
    }

    @Test
    fun `cancelBackgroundSync clears background sync and listener`() = runTest {
        // Suspend in authenticate so the background sync stays in-flight until we cancel it
        coEvery { transactionSyncManager.authenticate() } coAnswers { awaitCancellation() }

        syncManager.start(listener, "sync", listOf())
        syncManager.cancelBackgroundSync()

        verify(exactly = 0) { listener.onSyncComplete() }
        verify(exactly = 0) { listener.onSyncFailed(any()) }
    }

    @Test
    fun `shelf data is pushed before any table is pulled`() = runTest {
        coEvery { transactionSyncManager.authenticate() } returns true

        syncManager.start(listener, "sync", listOf())

        coVerifyOrder {
            userSyncRepository.uploadShelfData(userModel)
            transactionSyncManager.syncDb(any())
        }
    }

    @Test
    fun `cancellation while pushing shelf data aborts sync before any table is pulled`() = runTest {
        coEvery { transactionSyncManager.authenticate() } returns true
        coEvery { userSyncRepository.uploadShelfData(any()) } throws CancellationException("cancelled")

        syncManager.start(listener, "sync", listOf())

        coVerify(exactly = 0) { transactionSyncManager.syncDb(any()) }
    }

    @Test
    fun `syncPerf logging is evaluated when isVerbose returns true`() = runTest {
        io.mockk.mockkStatic(android.util.Log::class)
        every { syncTimeLogger.isVerbose } returns true
        every { android.util.Log.d(any(), any()) } returns 0

        coEvery { transactionSyncManager.authenticate() } returns true

        syncManager.start(listener, "sync", listOf())

        verify { android.util.Log.d("SyncPerf", match { it.contains("FULL SYNC STARTED") }) }
    }

    @Test
    fun `syncPerf logging is skipped when isVerbose returns false`() = runTest {
        io.mockk.mockkStatic(android.util.Log::class)
        every { syncTimeLogger.isVerbose } returns false
        every { android.util.Log.d(any(), any()) } returns 0

        coEvery { transactionSyncManager.authenticate() } returns true

        syncManager.start(listener, "sync", listOf())

        verify(exactly = 0) { android.util.Log.d("SyncPerf", any()) }
    }

    @Test
    fun `resourceTransactionSync skips removeDeletedResources when batch failure occurs`() = runTest {
        coEvery { transactionSyncManager.authenticate() } returns true

        val totalRowsJson = JsonObject().apply {
            addProperty("total_rows", 10)
        }
        val totalRowsResponse = Response.success(totalRowsJson)
        coEvery { apiInterface.getJsonObject(any(), match { it.contains("resources/_all_docs?limit=0") }) } returns totalRowsResponse
        coEvery { apiInterface.getJsonObject(any(), match { it.contains("resources/_all_docs?include_docs=true") }) } throws RuntimeException("Network error")

        syncManager.start(listener, "sync", listOf())

        coVerify(exactly = 0) { resourcesRepository.removeDeletedResources(any()) }
    }

    @Test
    fun `resourceTransactionSync calls removeDeletedResources with full id list when batch succeeds`() = runTest {
        coEvery { transactionSyncManager.authenticate() } returns true

        val totalRowsJson = JsonObject().apply {
            addProperty("total_rows", 2)
        }
        val totalRowsResponse = Response.success(totalRowsJson)
        coEvery { apiInterface.getJsonObject(any(), match { it.contains("resources/_all_docs?limit=0") }) } returns totalRowsResponse

        val doc1 = JsonObject().apply { addProperty("_id", "res_1") }
        val doc2 = JsonObject().apply { addProperty("_id", "res_2") }
        val row1 = JsonObject().apply { add("doc", doc1) }
        val row2 = JsonObject().apply { add("doc", doc2) }
        val rowsArray = JsonArray().apply {
            add(row1)
            add(row2)
        }
        val batchJson = JsonObject().apply {
            add("rows", rowsArray)
        }
        val batchResponse = Response.success(batchJson)
        coEvery { apiInterface.getJsonObject(any(), match { it.contains("resources/_all_docs?include_docs=true") }) } returns batchResponse
        coEvery { resourcesRepository.batchInsertResources(any()) } returns listOf("res_1", "res_2")

        syncManager.start(listener, "sync", listOf())

        coVerify(exactly = 1) { resourcesRepository.removeDeletedResources(listOf("res_1", "res_2")) }
    }
}
