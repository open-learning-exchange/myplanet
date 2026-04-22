package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmUser

@OptIn(ExperimentalCoroutinesApi::class)
class HealthRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var repository: HealthRepositoryImpl

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        databaseService = mockk(relaxed = true)
        repository = spyk(HealthRepositoryImpl(databaseService, testDispatcher))
    }

    @Test
    fun testSaveExamination() = runTest {
        val mockExamination = mockk<RealmHealthExamination>()
        val mockPojo = mockk<RealmHealthExamination>()
        val mockUser = mockk<RealmUser>()

        // Mock the underlying databaseService that base class executeTransaction delegates to
        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val transaction = firstArg<(io.realm.Realm) -> Unit>()
            // don't execute to avoid mocking realm internals, just verify it gets passed
        }

        repository.saveExamination(mockExamination, mockPojo, mockUser)

        coVerify(exactly = 1) {
            databaseService.executeTransactionAsync(any())
        }
    }

    @Test
    fun testUpdateExaminationUserId() = runTest {
        val testId = "test-id"
        val testUserId = "user-123"

        // Base class update delegates to executeTransactionAsync
        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val transaction = firstArg<(io.realm.Realm) -> Unit>()
        }

        repository.updateExaminationUserId(testId, testUserId)

        coVerify(exactly = 1) {
            databaseService.executeTransactionAsync(any())
        }
    }
}
