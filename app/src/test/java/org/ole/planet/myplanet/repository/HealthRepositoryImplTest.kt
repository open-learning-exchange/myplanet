package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import org.ole.planet.myplanet.data.applyEqualTo
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
        val mockExamination = RealmHealthExamination()
        val mockPojo = RealmHealthExamination()
        val mockUser = RealmUser()
        val mockRealm = mockk<io.realm.Realm>(relaxed = true)

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val transaction = firstArg<(io.realm.Realm) -> Unit>()
            transaction(mockRealm)
        }

        // Mock copyToRealmOrUpdate to return the passed object instead of a RealmModel mock
        io.mockk.every { mockRealm.copyToRealmOrUpdate(any<RealmUser>()) } returns mockUser
        io.mockk.every { mockRealm.copyToRealmOrUpdate(any<RealmHealthExamination>()) } returns mockExamination

        repository.saveExamination(mockExamination, mockPojo, mockUser)

        coVerify(exactly = 1) {
            mockRealm.copyToRealmOrUpdate(mockUser)
            mockRealm.copyToRealmOrUpdate(mockPojo)
            mockRealm.copyToRealmOrUpdate(mockExamination)
        }
    }

    @Test
    fun testUpdateExaminationUserId() = runTest {
        val testId = "test-id"
        val testUserId = "user-123"

        val mockRealm = mockk<io.realm.Realm>(relaxed = true)
        val mockQuery = mockk<io.realm.RealmQuery<RealmHealthExamination>>(relaxed = true)
        val mockExamination = mockk<RealmHealthExamination>(relaxed = true)

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val transaction = firstArg<(io.realm.Realm) -> Unit>()
            transaction(mockRealm)
        }

        io.mockk.every { mockRealm.where(RealmHealthExamination::class.java) } returns mockQuery

        io.mockk.mockkStatic("org.ole.planet.myplanet.data.DatabaseServiceKt")
        io.mockk.every {
            mockQuery.applyEqualTo("_id", testId as Any)
        } returns mockQuery

        io.mockk.every { mockQuery.findFirst() } returns mockExamination

        repository.updateExaminationUserId(testId, testUserId)

        coVerify(exactly = 1) {
            mockExamination.userId = testUserId
        }
    }
}
