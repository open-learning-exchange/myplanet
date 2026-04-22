package org.ole.planet.myplanet

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import io.mockk.*
import io.realm.Realm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.CoreDependenciesEntryPoint
import org.ole.planet.myplanet.model.RealmApkLog
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.VersionUtils

@OptIn(ExperimentalCoroutinesApi::class)
class MainApplicationTest {

    private lateinit var mockContext: MainApplication
    private lateinit var mockEntryPoint: CoreDependenciesEntryPoint
    private lateinit var mockServerUrlMapper: ServerUrlMapper
    private lateinit var mockUserSessionManager: UserSessionManager
    private lateinit var mockSpm: SharedPrefManager
    private lateinit var mockDatabaseService: DatabaseService
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockEntryPoint = mockk(relaxed = true)
        mockServerUrlMapper = mockk(relaxed = true)
        mockUserSessionManager = mockk(relaxed = true)
        mockSpm = mockk(relaxed = true)
        mockDatabaseService = mockk(relaxed = true)

        MainApplication.context = mockContext
        MainApplication.applicationScope = testScope

        mockkStatic(EntryPointAccessors::class)
        every { EntryPointAccessors.fromApplication(any(), CoreDependenciesEntryPoint::class.java) } returns mockEntryPoint
        every { mockEntryPoint.serverUrlMapper() } returns mockServerUrlMapper
        every { mockEntryPoint.userSessionManager() } returns mockUserSessionManager
        every { mockEntryPoint.sharedPrefManager() } returns mockSpm

        every { mockContext.applicationContext } returns mockContext
        every { mockContext.databaseService } returns mockDatabaseService

        val mockMapping = mockk<ServerUrlMapper.UrlMapping>(relaxed = true)
        every { mockMapping.alternativeUrl } returns null
        every { mockServerUrlMapper.processUrl(any()) } returns mockMapping

        mockkStatic(VersionUtils::class)
        every { VersionUtils.getVersionName(any()) } returns "1.0.0"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `isServerReachable tests dispatcher and returns false for invalid URL`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        var result: Boolean? = null

        launch(testDispatcher) {
            result = MainApplication.isServerReachable("invalid_url", testDispatcher)
        }

        // Before advancing, the coroutine has not completed
        assert(result == null)

        // Run the dispatcher
        advanceUntilIdle()

        // Since invalid_url will throw an exception or return false
        assertFalse(result == true)
    }

    @Test
    fun `test createLog`() = runTest(testDispatcher) {
        val mockUserModel = mockk<RealmUser>()
        every { mockUserModel.id } returns "user_123"
        coEvery { mockUserSessionManager.getUserModel() } returns mockUserModel

        every { mockSpm.getParentCode() } returns "parent_code"
        every { mockSpm.getPlanetCode() } returns "planet_code"

        val mockRealm = mockk<Realm>(relaxed = true)
        val mockLog = mockk<RealmApkLog>(relaxed = true)

        every { mockRealm.createObject(RealmApkLog::class.java, any<String>()) } returns mockLog

        coEvery { mockDatabaseService.executeTransactionAsync(any()) } answers {
            val transaction = arg<(Realm) -> Unit>(0)
            transaction(mockRealm)
        }

        MainApplication.createLog("test_type", "test_error")

        advanceUntilIdle()

        coVerify { mockDatabaseService.executeTransactionAsync(any()) }
        verify { mockLog.type = "test_type" }
        verify { mockLog.error = "test_error" }
        verify { mockLog.parentCode = "parent_code" }
        verify { mockLog.createdOn = "planet_code" }
        verify { mockLog.userId = "user_123" }
        verify { mockLog.version = "1.0.0" }
    }
}
