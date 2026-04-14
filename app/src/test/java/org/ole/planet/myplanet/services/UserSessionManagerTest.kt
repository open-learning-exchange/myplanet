package org.ole.planet.myplanet.services

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class UserSessionManagerTest {

    private lateinit var userSessionManager: UserSessionManager
    private val context: Context = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setup() {
        every { sharedPrefManager.getUserName() } returns "test_user"

        userSessionManager = UserSessionManager(
            context = context,
            sharedPrefManager = sharedPrefManager,
            applicationScope = testScope,
            userRepository = userRepository,
            activitiesRepository = activitiesRepository,
            dispatcherProvider = dispatcherProvider
        )
    }

    @Test
    fun `init retrieves user name successfully`() {
        verify { sharedPrefManager.getUserName() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `init throws IllegalArgumentException when sharedPrefManager throws`() {
        every { sharedPrefManager.getUserName() } throws IllegalArgumentException("Mock error")

        UserSessionManager(
            context = context,
            sharedPrefManager = sharedPrefManager,
            applicationScope = testScope,
            userRepository = userRepository,
            activitiesRepository = activitiesRepository,
            dispatcherProvider = dispatcherProvider
        )
    }

    @Test
    fun `getUserModel returns model from repository`() {
        val mockUser = mockk<RealmUser>()
        every { userRepository.getUserModel() } returns mockUser
        assertEquals(mockUser, userSessionManager.userModel)
        assertEquals(mockUser, userSessionManager.getUserModelCopy())
    }

    @Test
    fun `getUserModel suspending returns model from repository`() = testScope.runTest {
        val mockUser = mockk<RealmUser>()
        coEvery { userRepository.getUserModelSuspending() } returns mockUser
        assertEquals(mockUser, userSessionManager.getUserModel())
    }

    @Test
    fun `onLoginAsync invokes callback and logs login on success`() = testScope.runTest {
        val mockUser = mockk<RealmUser>(relaxed = true)
        every { mockUser.id } returns "id123"
        every { mockUser.name } returns "test_name"
        every { mockUser.parentCode } returns "pc123"
        every { mockUser.planetCode } returns "pl123"
        coEvery { userRepository.getUserModelSuspending() } returns mockUser

        var callbackInvoked = false
        val callback = { callbackInvoked = true }

        userSessionManager.onLoginAsync(callback = callback)
        advanceUntilIdle()

        coVerify {
            activitiesRepository.logLogin(
                userId = "id123",
                userName = "test_name",
                parentCode = "pc123",
                planetCode = "pl123"
            )
        }
        assertEquals(true, callbackInvoked)
    }

    @Test
    fun `onLoginAsync invokes onError on exception`() = testScope.runTest {
        val exception = RuntimeException("Mock Error")
        coEvery { userRepository.getUserModelSuspending() } throws exception

        var errorInvoked: Throwable? = null
        val onError: (Throwable) -> Unit = { errorInvoked = it }

        userSessionManager.onLoginAsync(onError = onError)
        advanceUntilIdle()

        assertEquals(exception, errorInvoked)
    }

    @Test
    fun `onLogin delegates to onLoginAsync`() = testScope.runTest {
        val mockUser = mockk<RealmUser>(relaxed = true)
        coEvery { userRepository.getUserModelSuspending() } returns mockUser
        userSessionManager.onLogin()
        advanceUntilIdle()
        coVerify { activitiesRepository.logLogin(any(), any(), any(), any()) }
    }


    @Test
    fun `logoutAsync logs logout successfully`() = testScope.runTest {
        val mockUser = mockk<RealmUser>(relaxed = true)
        every { mockUser.name } returns "test_name"
        coEvery { userRepository.getUserModelSuspending() } returns mockUser

        userSessionManager.logoutAsync()
        advanceUntilIdle()

        coVerify { activitiesRepository.logLogout("test_name") }
    }

    @Test
    fun `logoutAsync handles exception silently`() = testScope.runTest {
        coEvery { userRepository.getUserModelSuspending() } throws RuntimeException("Mock error")
        // Should not throw an unhandled exception
        userSessionManager.logoutAsync()
        advanceUntilIdle()
    }


    @Test
    fun `getGlobalLastVisit returns value from repository`() = testScope.runTest {
        coEvery { activitiesRepository.getGlobalLastVisit() } returns 12345L
        assertEquals(12345L, userSessionManager.getGlobalLastVisit())
    }

    @Test
    fun `getOfflineVisits returns count from repository for valid user`() = testScope.runTest {
        val mockUser = mockk<RealmUser>()
        every { mockUser.id } returns "id123"
        coEvery { activitiesRepository.getOfflineVisitCount("id123") } returns 5

        assertEquals(5, userSessionManager.getOfflineVisits(mockUser))
    }

    @Test
    fun `getOfflineVisits returns 0 for null user or id`() = testScope.runTest {
        assertEquals(0, userSessionManager.getOfflineVisits(null))

        val mockUser = mockk<RealmUser>()
        every { mockUser.id } returns null
        assertEquals(0, userSessionManager.getOfflineVisits(mockUser))
    }

    @Test
    fun `getLastVisit returns formatted string for valid timestamp`() = testScope.runTest {
        val mockUser = mockk<RealmUser>()
        every { mockUser.name } returns "test_name"
        val timestamp = 1672531200000L // Jan 1, 2023, 00:00:00 UTC (or local depending on timezone)
        coEvery { activitiesRepository.getLastVisit("test_name") } returns timestamp

        val expectedFormat = SimpleDateFormat("MMMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(timestamp))
        assertEquals(expectedFormat, userSessionManager.getLastVisit(mockUser))
    }

    @Test
    fun `getLastVisit returns fallback string when no record found`() = testScope.runTest {
        val mockUser = mockk<RealmUser>()
        every { mockUser.name } returns "test_name"
        coEvery { activitiesRepository.getLastVisit("test_name") } returns null

        assertEquals("No logout record found", userSessionManager.getLastVisit(mockUser))
    }

    @Test
    fun `setResourceOpenCount delegates to logResourceOpen for normal user`() = testScope.runTest {
        val mockUser = mockk<RealmUser>(relaxed = true)
        every { mockUser.id } returns "normal_user"
        every { mockUser.name } returns "test_name"
        every { mockUser.parentCode } returns "pc123"
        every { mockUser.planetCode } returns "pl123"
        coEvery { userRepository.getUserModelSuspending() } returns mockUser

        val mockLibrary = mockk<RealmMyLibrary>()
        every { mockLibrary.title } returns "test_title"
        every { mockLibrary.resourceId } returns "res123"

        userSessionManager.setResourceOpenCount(mockLibrary, "mock_type")
        advanceUntilIdle()

        coVerify {
            activitiesRepository.logResourceOpen(
                userName = "test_name",
                parentCode = "pc123",
                planetCode = "pl123",
                title = "test_title",
                resourceId = "res123",
                type = "mock_type"
            )
        }
    }

    @Test
    fun `setResourceOpenCount uses default type`() = testScope.runTest {
        val mockUser = mockk<RealmUser>(relaxed = true)
        every { mockUser.id } returns "normal_user"
        coEvery { userRepository.getUserModelSuspending() } returns mockUser

        val mockLibrary = mockk<RealmMyLibrary>(relaxed = true)

        userSessionManager.setResourceOpenCount(mockLibrary)
        advanceUntilIdle()

        coVerify {
            activitiesRepository.logResourceOpen(
                userName = any(),
                parentCode = any(),
                planetCode = any(),
                title = any(),
                resourceId = any(),
                type = UserSessionManager.KEY_RESOURCE_OPEN
            )
        }
    }


    @Test
    fun `setResourceOpenCount exits early for guest user`() = testScope.runTest {
        val mockUser = mockk<RealmUser>(relaxed = true)
        every { mockUser.id } returns "guest_user"
        coEvery { userRepository.getUserModelSuspending() } returns mockUser

        val mockLibrary = mockk<RealmMyLibrary>(relaxed = true)

        userSessionManager.setResourceOpenCount(mockLibrary)
        advanceUntilIdle()

        coVerify(exactly = 0) { activitiesRepository.logResourceOpen(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `getNumberOfResourceOpen returns formatted string when count is greater than 0`() = testScope.runTest {
        coEvery { activitiesRepository.getResourceOpenCount("test_user", UserSessionManager.KEY_RESOURCE_OPEN) } returns 5L
        assertEquals("Resource opened 5 times.", userSessionManager.getNumberOfResourceOpen())
    }

    @Test
    fun `getNumberOfResourceOpen returns empty string when count is 0`() = testScope.runTest {
        coEvery { activitiesRepository.getResourceOpenCount("test_user", UserSessionManager.KEY_RESOURCE_OPEN) } returns 0L
        assertEquals("", userSessionManager.getNumberOfResourceOpen())
    }

    @Test
    fun `maxOpenedResource returns formatted string when result is not null`() = testScope.runTest {
        coEvery { activitiesRepository.getMostOpenedResource("test_user", UserSessionManager.KEY_RESOURCE_OPEN) } returns Pair("Top Resource", 10)
        assertEquals("Top Resource opened 10 times", userSessionManager.maxOpenedResource())
    }

    @Test
    fun `maxOpenedResource returns empty string when result is null`() = testScope.runTest {
        coEvery { activitiesRepository.getMostOpenedResource("test_user", UserSessionManager.KEY_RESOURCE_OPEN) } returns null
        assertEquals("", userSessionManager.maxOpenedResource())
    }
}