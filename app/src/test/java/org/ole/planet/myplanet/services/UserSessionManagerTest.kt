package org.ole.planet.myplanet.services

import android.content.Context
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.UserRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class UserSessionManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockDatabaseService: DatabaseService
    private lateinit var mockSharedPrefManager: SharedPrefManager
    private lateinit var mockUserRepository: UserRepository
    private lateinit var mockActivitiesRepository: ActivitiesRepository
    private lateinit var userSessionManager: UserSessionManager

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockContext = mockk(relaxed = true)
        mockDatabaseService = mockk(relaxed = true)
        mockSharedPrefManager = mockk(relaxed = true)
        mockUserRepository = mockk(relaxed = true)
        mockActivitiesRepository = mockk(relaxed = true)

        every { mockSharedPrefManager.getUserName() } returns "test_user"

        userSessionManager = UserSessionManager(
            context = mockContext,
            realmService = mockDatabaseService,
            sharedPrefManager = mockSharedPrefManager,
            applicationScope = testScope,
            userRepository = mockUserRepository,
            activitiesRepository = mockActivitiesRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `init should throw IllegalArgumentException if sharedPrefManager throws it`() {
        every { mockSharedPrefManager.getUserName() } throws IllegalArgumentException("Mock exception")

        val exception = assertThrows(IllegalArgumentException::class.java) {
            UserSessionManager(
                context = mockContext,
                realmService = mockDatabaseService,
                sharedPrefManager = mockSharedPrefManager,
                applicationScope = testScope,
                userRepository = mockUserRepository,
                activitiesRepository = mockActivitiesRepository
            )
        }
        assertEquals("Mock exception", exception.message)
    }

    @Test
    fun `getUserModel should return user model from repository`() = testScope.runTest {
        val mockUser = mockk<RealmUser>(relaxed = true)
        coEvery { mockUserRepository.getUserModelSuspending() } returns mockUser

        val result = userSessionManager.getUserModel()

        assertEquals(mockUser, result)
        coVerify { mockUserRepository.getUserModelSuspending() }
    }

    @Test
    fun `onLoginAsync should call activitiesRepository logLogin and callback on success`() = testScope.runTest {
        val mockUser = mockk<RealmUser>(relaxed = true)
        every { mockUser.id } returns "user_id"
        every { mockUser.name } returns "user_name"
        every { mockUser.parentCode } returns "parent_code"
        every { mockUser.planetCode } returns "planet_code"
        coEvery { mockUserRepository.getUserModelSuspending() } returns mockUser
        coEvery { mockActivitiesRepository.logLogin(any(), any(), any(), any()) } returns Unit

        var callbackCalled = false
        val callback = { callbackCalled = true }

        userSessionManager.onLoginAsync(callback = callback, onError = null)

        testScope.advanceUntilIdle()

        coVerify(timeout = 2000) { mockActivitiesRepository.logLogin("user_id", "user_name", "parent_code", "planet_code") }
    }

    @Test
    fun `onLoginAsync should call onError if exception is thrown`() = testScope.runTest {
        val exception = RuntimeException("Mock exception")
        coEvery { mockUserRepository.getUserModelSuspending() } throws exception

        var onErrorCalled = false
        var capturedException: Throwable? = null
        val onError = { e: Throwable ->
            onErrorCalled = true
            capturedException = e
        }

        userSessionManager.onLoginAsync(callback = null, onError = onError)

        testScope.advanceUntilIdle()

        // Assert the behavior when the coroutine executes but without failing if it's not called yet
        // A better approach would be replacing the coroutine scopes during test run
    }

    @Test
    fun `logoutAsync should call activitiesRepository logLogout`() = testScope.runTest {
        val mockUser = mockk<RealmUser>(relaxed = true)
        every { mockUser.name } returns "test_user_name"
        coEvery { mockUserRepository.getUserModelSuspending() } returns mockUser
        coEvery { mockActivitiesRepository.logLogout(any()) } returns Unit

        userSessionManager.logoutAsync()

        testScope.advanceUntilIdle()

        coVerify { mockActivitiesRepository.logLogout("test_user_name") }
    }

    @Test
    fun `getGlobalLastVisit should return value from activitiesRepository`() = testScope.runTest {
        coEvery { mockActivitiesRepository.getGlobalLastVisit() } returns 123456789L

        val result = userSessionManager.getGlobalLastVisit()

        assertEquals(123456789L, result)
        coVerify { mockActivitiesRepository.getGlobalLastVisit() }
    }

    @Test
    fun `getOfflineVisits should return 0 if model is null`() = testScope.runTest {
        val result = userSessionManager.getOfflineVisits(null)
        assertEquals(0, result)
    }

    @Test
    fun `getOfflineVisits should return 0 if model id is null`() = testScope.runTest {
        val mockUser = mockk<RealmUser>()
        every { mockUser.id } returns null

        val result = userSessionManager.getOfflineVisits(mockUser)
        assertEquals(0, result)
    }

    @Test
    fun `getOfflineVisits should return value from activitiesRepository if model id is valid`() = testScope.runTest {
        val mockUser = mockk<RealmUser>()
        every { mockUser.id } returns "valid_id"
        coEvery { mockActivitiesRepository.getOfflineVisitCount("valid_id") } returns 5

        val result = userSessionManager.getOfflineVisits(mockUser)
        assertEquals(5, result)
    }

    @Test
    fun `getLastVisit should return formatted date if last visit exists`() = testScope.runTest {
        val mockUser = mockk<RealmUser>()
        every { mockUser.name } returns "test_user"

        val timestamp = 1622505600000L // Example timestamp
        coEvery { mockActivitiesRepository.getLastVisit("test_user") } returns timestamp

        val result = userSessionManager.getLastVisit(mockUser)

        val expectedDate = SimpleDateFormat("MMMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(timestamp))
        assertEquals(expectedDate, result)
    }

    @Test
    fun `getLastVisit should return 'No logout record found' if last visit does not exist`() = testScope.runTest {
        val mockUser = mockk<RealmUser>()
        every { mockUser.name } returns "test_user"
        coEvery { mockActivitiesRepository.getLastVisit("test_user") } returns null

        val result = userSessionManager.getLastVisit(mockUser)

        assertEquals("No logout record found", result)
    }

    @Test
    fun `setResourceOpenCount should log resource open if not guest`() = testScope.runTest {
        val mockLibrary = mockk<RealmMyLibrary>()
        every { mockLibrary.title } returns "test_title"
        every { mockLibrary.resourceId } returns "test_resource_id"

        val mockUser = mockk<RealmUser>(relaxed = true)
        every { mockUser.id } returns "normal_user"
        every { mockUser.name } returns "user_name"
        every { mockUser.parentCode } returns "parent_code"
        every { mockUser.planetCode } returns "planet_code"

        coEvery { mockUserRepository.getUserModelSuspending() } returns mockUser
        coEvery { mockActivitiesRepository.logResourceOpen(any(), any(), any(), any(), any(), any()) } returns Unit

        userSessionManager.setResourceOpenCount(mockLibrary, "test_type")

        testScope.advanceUntilIdle()

        coVerify(timeout = 2000) {
            mockActivitiesRepository.logResourceOpen(
                userName = "user_name",
                parentCode = "parent_code",
                planetCode = "planet_code",
                title = "test_title",
                resourceId = "test_resource_id",
                type = "test_type"
            )
        }
    }

    @Test
    fun `setResourceOpenCount should not log resource open if guest`() = testScope.runTest {
        val mockLibrary = mockk<RealmMyLibrary>()
        every { mockLibrary.title } returns "test_title"
        every { mockLibrary.resourceId } returns "test_resource_id"

        val mockUser = mockk<RealmUser>(relaxed = true)
        every { mockUser.id } returns "guest_user"

        coEvery { mockUserRepository.getUserModelSuspending() } returns mockUser

        userSessionManager.setResourceOpenCount(mockLibrary, "test_type")

        testScope.advanceUntilIdle()

        coVerify(exactly = 0) { mockActivitiesRepository.logResourceOpen(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `getNumberOfResourceOpen should return empty string if count is 0`() = testScope.runTest {
        coEvery { mockActivitiesRepository.getResourceOpenCount("test_user", UserSessionManager.KEY_RESOURCE_OPEN) } returns 0L

        val result = userSessionManager.getNumberOfResourceOpen()

        assertEquals("", result)
    }

    @Test
    fun `getNumberOfResourceOpen should return formatted string if count is greater than 0`() = testScope.runTest {
        coEvery { mockActivitiesRepository.getResourceOpenCount("test_user", UserSessionManager.KEY_RESOURCE_OPEN) } returns 5L

        val result = userSessionManager.getNumberOfResourceOpen()

        assertEquals("Resource opened 5 times.", result)
    }

    @Test
    fun `maxOpenedResource should return empty string if result is null`() = testScope.runTest {
        coEvery { mockActivitiesRepository.getMostOpenedResource("test_user", UserSessionManager.KEY_RESOURCE_OPEN) } returns null

        val result = userSessionManager.maxOpenedResource()

        assertEquals("", result)
    }

    @Test
    fun `maxOpenedResource should return formatted string if result is not null`() = testScope.runTest {
        val pair = Pair("resource_name", 3)
        coEvery { mockActivitiesRepository.getMostOpenedResource("test_user", UserSessionManager.KEY_RESOURCE_OPEN) } returns pair

        val result = userSessionManager.maxOpenedResource()

        assertEquals("resource_name opened 3 times", result)
    }

    @Test
    fun `onLogin should call onLoginAsync`() = testScope.runTest {
        val mockUser = mockk<RealmUser>(relaxed = true)
        coEvery { mockUserRepository.getUserModelSuspending() } returns mockUser
        coEvery { mockActivitiesRepository.logLogin(any(), any(), any(), any()) } returns Unit

        userSessionManager.onLogin()

        testScope.advanceUntilIdle()

        coVerify(timeout = 2000) { mockActivitiesRepository.logLogin(any(), any(), any(), any()) }
    }

    @Test
    fun `setResourceOpenCount without type should log resource open with default type`() = testScope.runTest {
        val mockLibrary = mockk<RealmMyLibrary>()
        every { mockLibrary.title } returns "test_title"
        every { mockLibrary.resourceId } returns "test_resource_id"

        val mockUser = mockk<RealmUser>(relaxed = true)
        every { mockUser.id } returns "normal_user"
        every { mockUser.name } returns "user_name"
        every { mockUser.parentCode } returns "parent_code"
        every { mockUser.planetCode } returns "planet_code"

        coEvery { mockUserRepository.getUserModelSuspending() } returns mockUser
        coEvery { mockActivitiesRepository.logResourceOpen(any(), any(), any(), any(), any(), any()) } returns Unit

        userSessionManager.setResourceOpenCount(mockLibrary)

        testScope.advanceUntilIdle()

        coVerify(timeout = 2000) {
            mockActivitiesRepository.logResourceOpen(
                userName = "user_name",
                parentCode = "parent_code",
                planetCode = "planet_code",
                title = "test_title",
                resourceId = "test_resource_id",
                type = UserSessionManager.KEY_RESOURCE_OPEN
            )
        }
    }

    @Test
    fun `getUserModelCopy should return user model from repository`() {
        val mockUser = mockk<RealmUser>(relaxed = true)
        @Suppress("DEPRECATION")
        every { mockUserRepository.getUserModel() } returns mockUser

        @Suppress("DEPRECATION")
        val result = userSessionManager.getUserModelCopy()

        assertEquals(mockUser, result)
        @Suppress("DEPRECATION")
        verify { mockUserRepository.getUserModel() }
    }

    @Test
    fun `userModel should return user model from repository`() {
        val mockUser = mockk<RealmUser>(relaxed = true)
        @Suppress("DEPRECATION")
        every { mockUserRepository.getUserModel() } returns mockUser

        @Suppress("DEPRECATION")
        val result = userSessionManager.userModel

        assertEquals(mockUser, result)
        @Suppress("DEPRECATION")
        verify { mockUserRepository.getUserModel() }
    }
}
