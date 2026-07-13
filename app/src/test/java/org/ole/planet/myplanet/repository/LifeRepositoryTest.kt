package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.services.SharedPrefManager

@OptIn(ExperimentalCoroutinesApi::class)
class LifeRepositoryTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var repository: LifeRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        databaseService = mockk(relaxed = true)
        val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
        every { sharedPrefManager.rawPreferences } returns mockk(relaxed = true)
        repository = LifeRepositoryImpl(
            databaseService,
            testDispatcher,
            sharedPrefManager,
            Gson(),
            CoroutineScope(testDispatcher)
        )
    }


    @Test
    fun updateVisibility_itemNotFound_doesNothing() = runTest {
        val myLifeId = "missing123"
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyLife>>(relaxed = true)

        every { mockRealm.where(RealmMyLife::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", myLifeId) } returns mockQuery
        every { mockQuery.findFirst() } returns null

        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(mockRealm)
        }

        repository.updateVisibility(true, myLifeId)

        coVerify(exactly = 1) { databaseService.executeTransactionAsync(any<Function1<Realm, Unit>>()) }
    }

    @Test
    fun updateMyLifeListOrder_itemNotInList_weightNotUpdated() = runTest {
        // The list contains item with _id "3"
        val mockLifeList = listOf(mockk<RealmMyLife>(relaxed = true).apply { every { _id } returns "3" })
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyLife>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmMyLife>>(relaxed = true)

        // The managed items retrieved from Realm have _id "1" and "2"
        val managedItem1 = mockk<RealmMyLife>(relaxed = true)
        every { managedItem1._id } returns "1"
        every { managedItem1.weight } returns 99

        val managedItem2 = mockk<RealmMyLife>(relaxed = true)
        every { managedItem2._id } returns "2"
        every { managedItem2.weight } returns 99

        every { mockResults.iterator() } returns mutableListOf(managedItem1, managedItem2).iterator()

        every { mockRealm.where(RealmMyLife::class.java) } returns mockQuery
        every { mockQuery.`in`("_id", arrayOf("3")) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(mockRealm)
        }

        repository.updateMyLifeListOrder(mockLifeList)

        // Verify that weight was never modified because index is -1
        io.mockk.verify(exactly = 0) { managedItem1.weight = any() }
        io.mockk.verify(exactly = 0) { managedItem2.weight = any() }
        assertEquals(99, managedItem1.weight)
        assertEquals(99, managedItem2.weight)
    }

    @Test
    fun getMyLifeByUserId_withEnsureLatestTrue_returnsEmptyList_noRefresh() = runTest {
        val userId = "user123"
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyLife>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmMyLife>>(relaxed = true)

        every { mockRealm.where(RealmMyLife::class.java) } returns mockQuery
        every { mockQuery.equalTo("userId", userId) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockRealm.copyFromRealm(mockResults) } returns emptyList<RealmMyLife>()

        // By passing true to ensureLatest, if tested via explicit withRealm, it expects refresh
        coEvery { databaseService.withRealmAsync<List<RealmMyLife>>(any()) } answers {
            val operation = firstArg<Function1<Realm, List<RealmMyLife>>>()
            operation.invoke(mockRealm)
        }

        val result = repository.getMyLifeByUserId(userId, ensureLatest = true)

        assertEquals(0, result.size)
    }
}
