package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.MyLifeDao
import org.ole.planet.myplanet.model.MyLife
import org.ole.planet.myplanet.services.SharedPrefManager

class LifeRepositoryImplTest {

    private lateinit var myLifeDao: MyLifeDao
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var gson: Gson
    private lateinit var repository: LifeRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Logger.getLogger("io.mockk").level = Level.OFF
        myLifeDao = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)
        mockSharedPreferences = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        every { sharedPrefManager.rawPreferences } returns mockSharedPreferences
        every { mockSharedPreferences.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit

        gson = Gson()
        repository = LifeRepositoryImpl(
            myLifeDao,
            testDispatcher,
            sharedPrefManager,
            gson,
            CoroutineScope(testDispatcher)
        )
    }

    @Test
    fun getMyLifeByUserId_returnsDaoResult() = runTest {
        val userId = "user123"
        val item1 = MyLife().apply { weight = 1; this.userId = userId }
        val item2 = MyLife().apply { weight = 2; this.userId = userId }
        coEvery { myLifeDao.getByUserId(userId) } returns listOf(item1, item2)

        val result = repository.getMyLifeByUserId(userId)

        assertEquals(2, result.size)
        assertEquals(1, result[0].weight)
        assertEquals(2, result[1].weight)
        coVerify(exactly = 1) { myLifeDao.getByUserId(userId) }
    }

    @Test
    fun getMyLifeByUserId_nullUser_returnsDaoResult() = runTest {
        val userId: String? = null
        coEvery { myLifeDao.getByUserId(null) } returns emptyList()

        val result = repository.getMyLifeByUserId(userId)

        assertEquals(0, result.size)
        coVerify(exactly = 1) { myLifeDao.getByUserId(null) }
    }

    @Test
    fun updateVisibility_delegatesToDao() = runTest {
        val myLifeId = "life123"

        repository.updateVisibility(false, myLifeId)

        coVerify(exactly = 1) { myLifeDao.updateVisibility(myLifeId, false) }
    }

    @Test
    fun updateMyLifeListOrder_updatesWeightBasedOnListIndex() = runTest {
        val item1 = MyLife().apply { _id = "1"; weight = 0; userId = "u" }
        val item2 = MyLife().apply { _id = "2"; weight = 0; userId = "u" }
        val list = listOf(item1, item2)

        // Managed rows come back with mismatched weights so both need updating.
        val managedItem1 = MyLife().apply { _id = "1"; weight = 5 }
        val managedItem2 = MyLife().apply { _id = "2"; weight = 5 }
        coEvery { myLifeDao.getByIds(any()) } returns listOf(managedItem1, managedItem2)
        coEvery { myLifeDao.getByUserId("u") } returns emptyList()

        val updatedSlot = slot<List<MyLife>>()
        coEvery { myLifeDao.update(capture(updatedSlot)) } returns Unit

        repository.updateMyLifeListOrder(list)

        assertEquals(0, managedItem1.weight)
        assertEquals(1, managedItem2.weight)
        coVerify(exactly = 1) { myLifeDao.update(any()) }
        assertEquals(2, updatedSlot.captured.size)
    }

    @Test
    fun updateMyLifeListOrder_emptyList_doesNothing() = runTest {
        repository.updateMyLifeListOrder(emptyList())

        coVerify(exactly = 0) { myLifeDao.getByIds(any()) }
        coVerify(exactly = 0) { myLifeDao.update(any()) }
    }

    @Test
    fun seedMyLifeIfEmpty_insertsItemsWhenNoExistingData() = runTest {
        val userId = "user123"
        val items = listOf(
            MyLife().apply { title = "Title1"; imageId = "img1"; this.userId = userId },
            MyLife().apply { title = "Title2"; imageId = "img2"; this.userId = userId }
        )
        coEvery { myLifeDao.countByUserId(userId) } returns 0

        val insertedItemsSlot = slot<List<MyLife>>()
        coEvery { myLifeDao.insertAll(capture(insertedItemsSlot)) } returns Unit

        repository.seedMyLifeIfEmpty(userId, items)

        coVerify(exactly = 1) { myLifeDao.insertAll(any()) }
        val insertedItems = insertedItemsSlot.captured
        assertEquals(2, insertedItems.size)
        assertEquals("Title1", insertedItems[0].title)
        assertEquals("img1", insertedItems[0].imageId)
        assertEquals(1, insertedItems[0].weight)
        assertEquals(userId, insertedItems[0].userId)
        assertTrue(insertedItems[0].isVisible)
        assertTrue(insertedItems[0]._id.isNotEmpty())
        assertEquals("Title2", insertedItems[1].title)
        assertEquals(2, insertedItems[1].weight)
    }

    @Test
    fun seedMyLifeIfEmpty_skipsWhenDataExists() = runTest {
        val userId = "user123"
        val items = listOf(MyLife().apply { title = "Title1"; this.userId = userId })
        coEvery { myLifeDao.countByUserId(userId) } returns 3

        repository.seedMyLifeIfEmpty(userId, items)

        coVerify(exactly = 0) { myLifeDao.insertAll(any()) }
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
        coEvery { myLifeDao.getByUserId(userId) } returns emptyList()

        val result = repository.getMyLifeForDashboard(userId, emptyList())

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

        val item1 = MyLife().apply { weight = 1; this.userId = userId; this.isVisible = true }
        val item2 = MyLife().apply { weight = 2; this.userId = userId; this.isVisible = true }
        coEvery { myLifeDao.getByUserId(userId) } returns listOf(item1, item2)

        val result = repository.getMyLifeForDashboard(userId, emptyList())

        assertEquals(2, result.size)
    }
}
