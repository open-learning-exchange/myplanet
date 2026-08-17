package org.ole.planet.myplanet.services

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.UserSyncRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.SecurePrefs

@OptIn(ExperimentalCoroutinesApi::class)
class UploadToShelfServiceTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var userRepository: UserRepository
    private lateinit var userSyncRepository: UserSyncRepository
    private lateinit var healthRepository: HealthRepository
    private lateinit var appScope: CoroutineScope
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var apiInterface: ApiInterface

    private lateinit var service: UploadToShelfService

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        userSyncRepository = mockk(relaxed = true)
        healthRepository = mockk(relaxed = true)

        appScope = TestScope(testDispatcher)

        dispatcherProvider = object : DispatcherProvider {
            override val main = testDispatcher
            override val io = testDispatcher
            override val default = testDispatcher
            override val unconfined = testDispatcher
        }

        apiInterface = mockk(relaxed = true)

        mockkObject(SecurePrefs)
        every { SecurePrefs.getPassword(context, sharedPreferences) } returns "testPassword"

        service = UploadToShelfService(
            context,
            sharedPreferences,
            sharedPrefManager,
            userRepository,
            userSyncRepository,
            healthRepository,
            appScope,
            dispatcherProvider,
            apiInterface
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `uploadUserData does nothing when no pending users`() = runTest(testDispatcher) {
        coEvery { userRepository.getPendingSyncUsers(100) } returns emptyList()
        val listener = mockk<OnSuccessListener>(relaxed = true)

        service.uploadUserData(listener)
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.getPendingSyncUsers(100) }
        verify(exactly = 0) { SecurePrefs.getPassword(any(), any()) }
    }

    @Test
    fun `uploadUserData processes users and uploads to shelf`() = runTest(testDispatcher) {
        val user1 = mockk<UserEntity>(relaxed = true)
        val user2 = mockk<UserEntity>(relaxed = true)
        coEvery { userRepository.getPendingSyncUsers(100) } returns listOf(user1, user2)
        coEvery { userSyncRepository.checkAndUploadUser(any(), any(), any()) } returns Unit
        coEvery { userRepository.getSyncedUsers() } returns emptyList()

        val listener = mockk<OnSuccessListener>(relaxed = true)

        service.uploadUserData(listener)
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.getPendingSyncUsers(100) }
        coVerify(exactly = 2) { userSyncRepository.checkAndUploadUser(any(), "testPassword", any()) }
        coVerify(exactly = 1) { userRepository.getSyncedUsers() }
        verify(exactly = 1) { listener.onSuccess("Sync with server completed successfully") }
    }

    @Test
    fun `uploadUserData handles errors gracefully`() = runTest(testDispatcher) {
        coEvery { userRepository.getPendingSyncUsers(100) } throws RuntimeException("Network error")
        val listener = mockk<OnSuccessListener>(relaxed = true)

        service.uploadUserData(listener)
        advanceUntilIdle()

        verify(exactly = 1) { listener.onSuccess("Error during user data sync: Network error") }
    }

    @Test
    fun `uploadSingleUserData uploads correct user data`() = runTest(testDispatcher) {
        val userName = "testUser"
        val user = mockk<UserEntity>(relaxed = true)
        coEvery { userRepository.getUserByName(userName) } returns user
        coEvery { userRepository.getSyncedUserByName(userName) } returns user
        coEvery { userSyncRepository.checkAndUploadUser(any(), any(), any()) } returns Unit
        coEvery { userSyncRepository.uploadShelfData(user) } returns Unit

        val listener = mockk<OnSuccessListener>(relaxed = true)

        service.uploadSingleUserData(userName, listener)
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.getUserByName(userName) }
        coVerify(exactly = 1) { userSyncRepository.checkAndUploadUser(user, "testPassword", any()) }
        coVerify(exactly = 1) { userRepository.getSyncedUserByName(userName) }
        coVerify(exactly = 1) { userSyncRepository.uploadShelfData(user) }
        verify(exactly = 1) { listener.onSuccess("Single user shelf sync completed successfully") }
    }

    @Test
    fun `uploadSingleUserData handles null userName gracefully`() = runTest(testDispatcher) {
        val listener = mockk<OnSuccessListener>(relaxed = true)

        service.uploadSingleUserData(null, listener)
        advanceUntilIdle()

        coVerify(exactly = 0) { userRepository.getUserByName(any()) }
        coVerify(exactly = 0) { userSyncRepository.checkAndUploadUser(any(), any(), any()) }
        verify(exactly = 1) { listener.onSuccess("Single user shelf sync completed successfully") }
    }

    @Test
    fun `uploadHealth uploads and marks health records`() = runTest(testDispatcher) {
        val healthRecords = mockk<List<HealthExamination>>(relaxed = true)
        val uploadedRecords = mockk<Map<String, String?>>(relaxed = true)

        coEvery { healthRepository.getUpdatedHealthExaminations() } returns healthRecords
        coEvery { healthRepository.uploadHealthData(healthRecords) } returns uploadedRecords
        coEvery { healthRepository.markHealthExaminationsUploaded(uploadedRecords) } returns Unit

        service.uploadHealth()
        advanceUntilIdle()

        coVerify(exactly = 1) { healthRepository.getUpdatedHealthExaminations() }
        coVerify(exactly = 1) { healthRepository.uploadHealthData(healthRecords) }
        coVerify(exactly = 1) { healthRepository.markHealthExaminationsUploaded(uploadedRecords) }
    }

    @Test
    fun `uploadSingleUserHealth returns if userId is null or empty`() = runTest(testDispatcher) {
        val listener = mockk<OnSuccessListener>(relaxed = true)

        service.uploadSingleUserHealth(null, listener)
        advanceUntilIdle()

        coVerify(exactly = 0) { healthRepository.getUpdatedHealthForUser(any()) }

        service.uploadSingleUserHealth("", listener)
        advanceUntilIdle()

        coVerify(exactly = 0) { healthRepository.getUpdatedHealthForUser(any()) }
    }

    @Test
    fun `uploadSingleUserHealth uploads data for specific user`() = runTest(testDispatcher) {
        val userId = "user123"
        val listener = mockk<OnSuccessListener>(relaxed = true)
        val healthRecords = mockk<List<HealthExamination>>(relaxed = true)
        val uploadedRecords = mockk<Map<String, String?>>(relaxed = true)

        coEvery { healthRepository.getUpdatedHealthForUser(userId) } returns healthRecords
        coEvery { healthRepository.uploadHealthData(healthRecords) } returns uploadedRecords
        coEvery { healthRepository.markHealthExaminationsUploaded(uploadedRecords) } returns Unit

        service.uploadSingleUserHealth(userId, listener)
        advanceUntilIdle()

        coVerify(exactly = 1) { healthRepository.getUpdatedHealthForUser(userId) }
        coVerify(exactly = 1) { healthRepository.uploadHealthData(healthRecords) }
        coVerify(exactly = 1) { healthRepository.markHealthExaminationsUploaded(uploadedRecords) }
        verify(exactly = 1) { listener.onSuccess("Health data for user $userId uploaded successfully") }
    }

    @Test
    fun `uploadSingleUserHealth handles errors gracefully`() = runTest(testDispatcher) {
        val userId = "user123"
        val listener = mockk<OnSuccessListener>(relaxed = true)

        coEvery { healthRepository.getUpdatedHealthForUser(userId) } throws RuntimeException("Health error")

        service.uploadSingleUserHealth(userId, listener)
        advanceUntilIdle()

        verify(exactly = 1) { listener.onSuccess("Error uploading health data for user $userId: Health error") }
    }

    @Test
    fun `uploadToShelf handles empty unmanaged users`() = runTest(testDispatcher) {
        // Mock getPendingSyncUsers to return some users so uploadUserData proceeds to uploadToShelf
        val user1 = mockk<UserEntity>(relaxed = true)
        coEvery { userRepository.getPendingSyncUsers(100) } returns listOf(user1)
        coEvery { userSyncRepository.checkAndUploadUser(any(), any(), any()) } returns Unit

        // Mock getSyncedUsers for uploadToShelf
        coEvery { userRepository.getSyncedUsers() } returns emptyList()
        val listener = mockk<OnSuccessListener>(relaxed = true)

        service.uploadUserData(listener)
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.getSyncedUsers() }
        verify(exactly = 1) { listener.onSuccess("Sync with server completed successfully") }
    }

    @Test
    fun `uploadToShelf uploads all synced users to shelf successfully`() = runTest(testDispatcher) {
        val user1 = mockk<UserEntity>(relaxed = true)
        coEvery { userRepository.getPendingSyncUsers(100) } returns listOf(user1)
        coEvery { userSyncRepository.checkAndUploadUser(any(), any(), any()) } returns Unit

        val unmanagedUsers = listOf(user1)
        coEvery { userRepository.getSyncedUsers() } returns unmanagedUsers
        coEvery { userSyncRepository.uploadAllSyncedUsersToShelf(unmanagedUsers) } returns Result.success(Unit)

        val listener = mockk<OnSuccessListener>(relaxed = true)

        service.uploadUserData(listener)
        advanceUntilIdle()

        coVerify(exactly = 1) { userSyncRepository.uploadAllSyncedUsersToShelf(unmanagedUsers) }
        verify(exactly = 1) { listener.onSuccess("Sync with server completed successfully") }
    }

    @Test
    fun `uploadToShelf handles upload failure`() = runTest(testDispatcher) {
        val user1 = mockk<UserEntity>(relaxed = true)
        coEvery { userRepository.getPendingSyncUsers(100) } returns listOf(user1)
        coEvery { userSyncRepository.checkAndUploadUser(any(), any(), any()) } returns Unit

        val unmanagedUsers = listOf(user1)
        coEvery { userRepository.getSyncedUsers() } returns unmanagedUsers
        val errorMsg = "Shelf error"
        coEvery { userSyncRepository.uploadAllSyncedUsersToShelf(unmanagedUsers) } returns Result.failure(Exception(errorMsg))

        val listener = mockk<OnSuccessListener>(relaxed = true)

        service.uploadUserData(listener)
        advanceUntilIdle()

        coVerify(exactly = 1) { userSyncRepository.uploadAllSyncedUsersToShelf(unmanagedUsers) }
        verify(exactly = 1) { listener.onSuccess("Unable to update documents: $errorMsg") }
    }
}
