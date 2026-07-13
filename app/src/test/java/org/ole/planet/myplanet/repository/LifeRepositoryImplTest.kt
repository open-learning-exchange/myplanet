package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.services.SharedPrefManager

class LifeRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var gson: Gson
    private lateinit var repository: LifeRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Logger.getLogger("io.mockk").level = Level.OFF
        databaseService = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)
        mockSharedPreferences = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        every { sharedPrefManager.rawPreferences } returns mockSharedPreferences
        every { mockSharedPreferences.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit

        gson = Gson()
        repository = LifeRepositoryImpl(
            databaseService,
            testDispatcher,
            sharedPrefManager,
            gson,
            CoroutineScope(testDispatcher)
        )
    }


    @Test
    fun getMyLifeByUserId_returnsSortedItems() = runTest {
        val userId = "user123"
        val item1 = RealmMyLife().apply { weight = 2; this.userId = userId }
        val item2 = RealmMyLife().apply { weight = 1; this.userId = userId }
        val unsortedList = mutableListOf(item1, item2)

        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyLife>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmMyLife>>(relaxed = true)

        every { mockRealm.where(RealmMyLife::class.java) } returns mockQuery
        every { mockQuery.equalTo("userId", userId) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockRealm.copyFromRealm(mockResults) } returns unsortedList

        val operationSlot = slot<Function1<Realm, List<RealmMyLife>>>()
        coEvery { databaseService.withRealmAsync(capture(operationSlot)) } answers {
            operationSlot.captured.invoke(mockRealm)
        }

        val result = repository.getMyLifeByUserId(userId)

        assertEquals(2, result.size)
        assertEquals(1, result[0].weight)
        assertEquals(2, result[1].weight)
    }

    @Test
    fun getMyLifeByUserId_nullUser_returnsSortedItems() = runTest {
        val userId: String? = null
        val item1 = RealmMyLife().apply { weight = 2; this.userId = userId }
        val item2 = RealmMyLife().apply { weight = 1; this.userId = userId }
        val unsortedList = mutableListOf(item1, item2)

        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyLife>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmMyLife>>(relaxed = true)

        every { mockRealm.where(RealmMyLife::class.java) } returns mockQuery
        every { mockQuery.equalTo("userId", userId) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockRealm.copyFromRealm(mockResults) } returns unsortedList

        val operationSlot = slot<Function1<Realm, List<RealmMyLife>>>()
        coEvery { databaseService.withRealmAsync(capture(operationSlot)) } answers {
            operationSlot.captured.invoke(mockRealm)
        }

        val result = repository.getMyLifeByUserId(userId)

        assertEquals(2, result.size)
        assertEquals(1, result[0].weight)
        assertEquals(2, result[1].weight)
    }

    @Test
    fun updateVisibility_callsExecuteTransactionAndTogglesIsVisible() = runTest {
        val myLifeId = "life123"
        val isVisible = false
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyLife>>(relaxed = true)
        val mockLife = RealmMyLife().apply { this.isVisible = true }

        every { mockRealm.where(RealmMyLife::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", myLifeId) } returns mockQuery
        every { mockQuery.findFirst() } returns mockLife

        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(mockRealm)
        }

        repository.updateVisibility(isVisible, myLifeId)

        assertEquals(false, mockLife.isVisible)
        coVerify(exactly = 1) { databaseService.executeTransactionAsync(any<Function1<Realm, Unit>>()) }
    }

    @Test
    fun updateMyLifeListOrder_updatesWeightBasedOnListIndex() = runTest {
        val item1 = RealmMyLife().apply { _id = "1"; weight = 0 }
        val item2 = RealmMyLife().apply { _id = "2"; weight = 0 }
        val list = listOf(item1, item2)
        val ids = arrayOf("1", "2")

        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyLife>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmMyLife>>(relaxed = true)
        val managedItem1 = RealmMyLife().apply { _id = "2"; weight = 0 }
        val managedItem2 = RealmMyLife().apply { _id = "1"; weight = 0 }

        // Setting up the mock Results as an iterator
        every { mockResults.iterator() } returns mutableListOf(managedItem1, managedItem2).iterator()

        every { mockRealm.where(RealmMyLife::class.java) } returns mockQuery
        every { mockQuery.`in`("_id", ids) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(mockRealm)
        }

        repository.updateMyLifeListOrder(list)

        // managedItem1 (_id = "2") should have weight = index in list (1)
        assertEquals(1, managedItem1.weight)
        // managedItem2 (_id = "1") should have weight = index in list (0)
        assertEquals(0, managedItem2.weight)
        coVerify(exactly = 1) { databaseService.executeTransactionAsync(any<Function1<Realm, Unit>>()) }
    }

    @Test
    fun updateMyLifeListOrder_emptyList_doesNothing() = runTest {
        val list = emptyList<RealmMyLife>()

        val transactionSlot = slot<Function1<Realm, Unit>>()
        val mockRealm = mockk<Realm>(relaxed = true)
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(mockRealm)
        }

        repository.updateMyLifeListOrder(list)

        coVerify(exactly = 1) { databaseService.executeTransactionAsync(any<Function1<Realm, Unit>>()) }
        io.mockk.verify(exactly = 0) { mockRealm.where(RealmMyLife::class.java) }
    }

    @Test
    fun seedMyLifeIfEmpty_insertsItemsWhenNoExistingData() = runTest {
        val userId = "user123"
        val items = listOf(
            RealmMyLife().apply { title = "Title1"; imageId = "img1"; this.userId = userId },
            RealmMyLife().apply { title = "Title2"; imageId = "img2"; this.userId = userId }
        )

        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyLife>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmMyLife>>(relaxed = true)

        every { mockRealm.where(RealmMyLife::class.java) } returns mockQuery
        every { mockQuery.equalTo("userId", userId) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockResults.isEmpty() } returns true

        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(mockRealm)
        }

        val insertedItemsSlot = slot<List<RealmMyLife>>()
        every { mockRealm.insertOrUpdate(capture(insertedItemsSlot)) } returns Unit

        repository.seedMyLifeIfEmpty(userId, items)

        coVerify(exactly = 1) { databaseService.executeTransactionAsync(any<Function1<Realm, Unit>>()) }
        io.mockk.verify(exactly = 1) { mockRealm.insertOrUpdate(any<List<RealmMyLife>>()) }

        val insertedItems = insertedItemsSlot.captured
        assertEquals(2, insertedItems.size)

        assertEquals("Title1", insertedItems[0].title)
        assertEquals("img1", insertedItems[0].imageId)
        assertEquals(1, insertedItems[0].weight)
        assertEquals(userId, insertedItems[0].userId)
        assertTrue(insertedItems[0].isVisible)
        assertTrue(insertedItems[0]._id!!.isNotEmpty())

        assertEquals("Title2", insertedItems[1].title)
        assertEquals("img2", insertedItems[1].imageId)
        assertEquals(2, insertedItems[1].weight)
        assertEquals(userId, insertedItems[1].userId)
        assertTrue(insertedItems[1].isVisible)
        assertTrue(insertedItems[1]._id!!.isNotEmpty())
    }

    @Test
    fun seedMyLifeIfEmpty_nullUser_insertsItemsWhenNoExistingData() = runTest {
        val userId: String? = null
        val items = listOf(
            RealmMyLife().apply { title = "Title1"; imageId = "img1"; this.userId = userId }
        )

        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyLife>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmMyLife>>(relaxed = true)

        every { mockRealm.where(RealmMyLife::class.java) } returns mockQuery
        every { mockQuery.equalTo("userId", userId) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockResults.isEmpty() } returns true

        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(mockRealm)
        }

        val insertedItemsSlot = slot<List<RealmMyLife>>()
        every { mockRealm.insertOrUpdate(capture(insertedItemsSlot)) } returns Unit

        repository.seedMyLifeIfEmpty(userId, items)

        coVerify(exactly = 1) { databaseService.executeTransactionAsync(any<Function1<Realm, Unit>>()) }
        io.mockk.verify(exactly = 1) { mockRealm.insertOrUpdate(any<List<RealmMyLife>>()) }

        val insertedItems = insertedItemsSlot.captured
        assertEquals(1, insertedItems.size)

        assertEquals("Title1", insertedItems[0].title)
        assertEquals("img1", insertedItems[0].imageId)
        assertEquals(1, insertedItems[0].weight)
        assertEquals(userId, insertedItems[0].userId)
        assertTrue(insertedItems[0].isVisible)
        assertTrue(insertedItems[0]._id!!.isNotEmpty())
    }

    @Test
    fun seedMyLifeIfEmpty_skipsWhenDataExists() = runTest {
        val userId = "user123"
        val items = listOf(
            RealmMyLife().apply { title = "Title1"; imageId = "img1"; this.userId = userId }
        )

        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyLife>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmMyLife>>(relaxed = true)

        every { mockRealm.where(RealmMyLife::class.java) } returns mockQuery
        every { mockQuery.equalTo("userId", userId) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockResults.isEmpty() } returns false // Data exists

        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(mockRealm)
        }

        repository.seedMyLifeIfEmpty(userId, items)

        coVerify(exactly = 1) { databaseService.executeTransactionAsync(any<Function1<Realm, Unit>>()) }
        io.mockk.verify(exactly = 0) { mockRealm.insertOrUpdate(any<List<RealmMyLife>>()) }
    }

    @Test
    fun getMyLifeForDashboard_validJson() = runTest {
        val userId = "123"
        val expectedItems = listOf(
            CachedMyLifeItem("img1", "Title 1", true, 1),
            CachedMyLifeItem("img2", "Title 2", false, 2)
        )
        val json = Gson().toJson(expectedItems)

        every { mockSharedPreferences.getString("myLifeCache_$userId", null) } returns json

        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyLife>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmMyLife>>(relaxed = true)
        every { mockRealm.where(RealmMyLife::class.java) } returns mockQuery
        every { mockQuery.equalTo("userId", userId) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockRealm.copyFromRealm(mockResults) } returns emptyList()

        val operationSlot = io.mockk.slot<(Realm) -> List<RealmMyLife>>()
        coEvery { databaseService.withRealmAsync<List<RealmMyLife>>(capture(operationSlot)) } answers {
            operationSlot.captured.invoke(mockRealm)
        }

        val result = repository.getMyLifeForDashboard(userId, emptyList())

        // Returns only visible item
        assertEquals(1, result.size)
        assertEquals("img1", result[0].imageId)
        assertEquals("Title 1", result[0].title)
        assertEquals(true, result[0].isVisible)
        assertEquals(1, result[0].weight)
    }

    @Test
    fun getMyLifeForDashboard_invalidJson() = runTest {
        val userId = "789"
        every { mockSharedPreferences.getString("myLifeCache_$userId", null) } returns "invalid_json"

        val item1 = RealmMyLife().apply { weight = 2; this.userId = userId; this.isVisible = true }
        val item2 = RealmMyLife().apply { weight = 1; this.userId = userId; this.isVisible = true }
        val unsortedList = mutableListOf(item1, item2)

        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyLife>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmMyLife>>(relaxed = true)

        every { mockRealm.where(RealmMyLife::class.java) } returns mockQuery
        every { mockQuery.equalTo("userId", userId) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockRealm.copyFromRealm(mockResults) } returns unsortedList

        val operationSlot = io.mockk.slot<(Realm) -> List<RealmMyLife>>()
        coEvery { databaseService.withRealmAsync<List<RealmMyLife>>(capture(operationSlot)) } answers {
            operationSlot.captured.invoke(mockRealm)
        }

        val result = repository.getMyLifeForDashboard(userId, emptyList())
        assertEquals(2, result.size)
    }
}
