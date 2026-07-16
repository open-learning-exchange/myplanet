package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.JsonObject
import dagger.Lazy
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class UserRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var settings: SharedPreferences
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var apiInterface: ApiInterface
    private lateinit var uploadToShelfService: Lazy<UploadToShelfService>
    private lateinit var context: Context
    private lateinit var configurationsRepository: ConfigurationsRepository
    private lateinit var appScope: CoroutineScope
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var activitiesRepository: ActivitiesRepository
    private lateinit var activitiesRepositoryLazy: dagger.Lazy<ActivitiesRepository>

    private lateinit var repository: UserRepositoryImpl

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkObject(UrlUtils)
        every { UrlUtils.header } returns "Basic auth"
        every { UrlUtils.getUrl() } returns "http://test.url"

        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        databaseService = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)
        apiInterface = mockk(relaxed = true)
        uploadToShelfService = mockk(relaxed = true)
        context = mockk(relaxed = true)
        configurationsRepository = mockk(relaxed = true)
        appScope = TestScope(testDispatcher)

        activitiesRepository = mockk(relaxed = true)
        activitiesRepositoryLazy = mockk(relaxed = true)
        every { activitiesRepositoryLazy.get() } returns activitiesRepository

        dispatcherProvider = mockk(relaxed = true)
        every { dispatcherProvider.io } returns testDispatcher
        every { dispatcherProvider.main } returns testDispatcher
        every { dispatcherProvider.default } returns testDispatcher
        every { dispatcherProvider.unconfined } returns testDispatcher

        repository = UserRepositoryImpl(
            databaseService,
            UnconfinedTestDispatcher(),
            settings,
            sharedPrefManager,
            apiInterface,
            mockk(relaxed = true),
            mockk(relaxed = true),
            uploadToShelfService,
            context,
            configurationsRepository,
            appScope,
            dispatcherProvider,
            activitiesRepositoryLazy
        )
    }

    @After
    fun tearDown() {
        unmockkObject(UrlUtils)
        unmockkStatic(Log::class)
    }

    @Test
    fun `getDashboardProfile uses user name if fullName is blank`() = runTest(testDispatcher) {
        val user = RealmUser().apply { name = "john"; firstName = "  "; lastName = "  " }
        val spiedRepo = spyk(repository)
        coEvery { spiedRepo.getUserById("123") } returns user
        coEvery { activitiesRepository.getOfflineLoginCount("john") } returns 5

        val result = spiedRepo.getDashboardProfile("123")

        assertEquals("john", result.fullName)
        assertEquals(5, result.offlineLogins)
    }

    @Test
    fun `getDashboardProfile handles null user`() = runTest(testDispatcher) {
        val spiedRepo = spyk(repository)
        coEvery { spiedRepo.getUserById("123") } returns null

        val result = spiedRepo.getDashboardProfile("123")

        assertEquals(null, result.fullName)
        assertEquals(0, result.offlineLogins)
        io.mockk.coVerify(exactly = 0) { activitiesRepository.getOfflineLoginCount(any()) }
    }

    @Test
    fun `getDashboardProfile handles null user name`() = runTest(testDispatcher) {
        val user = RealmUser().apply { name = null; firstName = "John"; lastName = "Doe" }
        val spiedRepo = spyk(repository)
        coEvery { spiedRepo.getUserById("123") } returns user

        val result = spiedRepo.getDashboardProfile("123")

        assertEquals("John Doe", result.fullName)
        assertEquals(0, result.offlineLogins)
        io.mockk.coVerify(exactly = 0) { activitiesRepository.getOfflineLoginCount(any()) }
    }

    @Test
    fun `becomeMember uses dispatcherProvider IO`() = runTest(testDispatcher) {
        val userName = "testUser"
        val userObj = JsonObject().apply { addProperty("name", userName) }
        val userUrl = "http://test.url/_users/org.couchdb.user:$userName"
        val errorMessage = "User already exists"

        coEvery { configurationsRepository.checkServerAvailability() } returns true
        every { context.getString(R.string.unable_to_create_user_user_already_exists) } returns errorMessage

        // Mock API response to simulate user already exists
        val existsResponseBody = JsonObject().apply { addProperty("_id", "some_id") }
        val response = Response.success(existsResponseBody)
        coEvery { apiInterface.getJsonObject("Basic auth", userUrl) } returns response

        val result = repository.becomeMember(userObj)
        advanceUntilIdle()

        assertFalse(result.first)
        assertEquals(errorMessage, result.second)
    }

    @Test
    fun `becomeMember succeeds on happy path`() = runTest(testDispatcher) {
        val userName = "newUser"
        val userObj = JsonObject().apply { addProperty("name", userName) }
        val userUrl = "http://test.url/_users/org.couchdb.user:$userName"
        val successMessage = "User created successfully"
        val id = "new_user_id"

        coEvery { configurationsRepository.checkServerAvailability() } returns true
        every { context.getString(R.string.user_created_successfully) } returns successMessage

        // 1. User doesn't exist check
        val notExistsResponseBody = JsonObject()
        val notFoundResponse = Response.success(notExistsResponseBody)
        coEvery { apiInterface.getJsonObject("Basic auth", userUrl) } returns notFoundResponse

        // 2. User creation mock
        val createdResponseBody = JsonObject().apply { addProperty("id", id) }
        val createdResponse = Response.success(createdResponseBody)
        coEvery { apiInterface.putDoc(null, "application/json", userUrl, userObj) } returns createdResponse

        // 3. User save to db fetch
        val userFetchUrl = "http://test.url/_users/$id"
        val userFetchResponse = Response.success(JsonObject().apply {
            addProperty("_id", id)
            addProperty("name", userName)
        })
        coEvery { apiInterface.getJsonObject("Basic auth", userFetchUrl) } returns userFetchResponse

        // Stub saveUser to return a mocked RealmUser instead of attempting DB operations
        val spyRepository = spyk(repository)
        val mockRealmUser = mockk<RealmUser>(relaxed = true)
        coEvery { spyRepository.saveUser(any(), any(), any()) } returns mockRealmUser

        val result = spyRepository.becomeMember(userObj)
        advanceUntilIdle()

        assertEquals(true, result.first)
        assertEquals(successMessage, result.second)
    }

    @Test
    fun `hasAtLeastOneUser returns true when user exists`() = runTest {
        val mockRealm = mockk<io.realm.Realm>(relaxed = true)
        val mockRealmQuery = mockk<io.realm.RealmQuery<RealmUser>>()
        coEvery { databaseService.withRealmAsync<Long>(any()) } answers {
            val block = firstArg<(io.realm.Realm) -> Long>()
            block(mockRealm)
        }
        every { mockRealm.where(RealmUser::class.java) } returns mockRealmQuery
        every { mockRealmQuery.count() } returns 1L

        val result = repository.hasAtLeastOneUser()
        assertEquals(true, result)
    }

    @Test
    fun `hasAtLeastOneUser returns false when no user exists`() = runTest {
        val mockRealm = mockk<io.realm.Realm>(relaxed = true)
        val mockRealmQuery = mockk<io.realm.RealmQuery<RealmUser>>()
        coEvery { databaseService.withRealmAsync<Long>(any()) } answers {
            val block = firstArg<(io.realm.Realm) -> Long>()
            block(mockRealm)
        }
        every { mockRealm.where(RealmUser::class.java) } returns mockRealmQuery
        every { mockRealmQuery.count() } returns 0L

        val result = repository.hasAtLeastOneUser()
        assertEquals(false, result)
    }

    @Test
    fun `upsertSavedUser adds a new guest`() = runTest {
        every { sharedPrefManager.getSavedUsers() } returns emptyList()
        val savedSlot = slot<List<User>>()
        every { sharedPrefManager.setSavedUsers(capture(savedSlot)) } just Runs

        repository.upsertSavedUser("guest1", "encrypted", "guest", null, null)

        assertEquals(1, savedSlot.captured.size)
        assertEquals("guest1", savedSlot.captured[0].name)
        assertEquals("guest", savedSlot.captured[0].source)
    }

    @Test
    fun `upsertSavedUser replaces existing guest with the same name`() = runTest {
        val existing = User("", "guest1", "oldPwd", "", "guest")
        every { sharedPrefManager.getSavedUsers() } returns listOf(existing)
        val savedSlot = slot<List<User>>()
        every { sharedPrefManager.setSavedUsers(capture(savedSlot)) } just Runs

        repository.upsertSavedUser("guest1", "newPwd", "guest", null, null)

        assertEquals(1, savedSlot.captured.size)
        assertEquals("newPwd", savedSlot.captured[0].password)
    }

    @Test
    fun `upsertSavedUser replaces existing member with the same username`() = runTest {
        val existing = User("user1", "Full Name", "oldPwd", "old.jpg", "member")
        every { sharedPrefManager.getSavedUsers() } returns listOf(existing)
        val savedSlot = slot<List<User>>()
        every { sharedPrefManager.setSavedUsers(capture(savedSlot)) } just Runs

        repository.upsertSavedUser("Full Name", "newPwd", "member", "new.jpg", "user1")

        assertEquals(1, savedSlot.captured.size)
        assertEquals("newPwd", savedSlot.captured[0].password)
        assertEquals("new.jpg", savedSlot.captured[0].image)
    }

    @Test
    fun `resetGuestAsMember removes saved users matching the username`() = runTest {
        val guest = User("", "guest1", "pwd", "", "guest")
        val other = User("Full Name", "user2", "pwd", "", "member")
        every { sharedPrefManager.getSavedUsers() } returns listOf(guest, other)
        val savedSlot = slot<List<User>>()
        every { sharedPrefManager.setSavedUsers(capture(savedSlot)) } just Runs

        repository.resetGuestAsMember("guest1")

        assertEquals(listOf(other), savedSlot.captured)
    }

    @Test
    fun `resetGuestAsMember does nothing when the username is not saved`() = runTest {
        every { sharedPrefManager.getSavedUsers() } returns listOf(User("Full Name", "user2", "pwd", "", "member"))
        repository.resetGuestAsMember("guest1")
        verify(exactly = 0) { sharedPrefManager.setSavedUsers(any()) }
    }

    @Test
    fun `upsertSavedUser ignores unknown sources`() = runTest {
        every { sharedPrefManager.getSavedUsers() } returns emptyList()

        repository.upsertSavedUser("someone", "pwd", "unknown", null, null)

        verify(exactly = 0) { sharedPrefManager.setSavedUsers(any()) }
    }
}
