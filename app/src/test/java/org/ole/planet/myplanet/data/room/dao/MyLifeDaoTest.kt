package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.MyLife

@RunWith(AndroidJUnit4::class)
class MyLifeDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var myLifeDao: MyLifeDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        myLifeDao = database.myLifeDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun getByUserId_ordersByWeightAscending() = runBlocking {
        val item1 = MyLife("img1", "user1", "Health").apply { _id = "1"; weight = 2 }
        val item2 = MyLife("img2", "user1", "Calendar").apply { _id = "2"; weight = 1 }
        val item3 = MyLife("img3", "user1", "Surveys").apply { _id = "3"; weight = 3 }
        myLifeDao.insertAll(listOf(item1, item2, item3))

        val list = myLifeDao.getByUserId("user1")
        assertEquals(3, list.size)
        assertEquals("Calendar", list[0].title)
        assertEquals("Health", list[1].title)
        assertEquals("Surveys", list[2].title)
    }

    @Test
    fun getByUserId_doesNotMixDifferentUsersOrNullUsers() = runBlocking {
        val userItem = MyLife("img1", "user1", "User1 Health").apply { _id = "1"; weight = 1 }
        val nullUserItem = MyLife("img1", null, "Guest Health").apply { _id = "2"; weight = 0 }
        val otherUserItem = MyLife("img1", "user2", "User2 Health").apply { _id = "3"; weight = 0 }
        myLifeDao.insertAll(listOf(userItem, nullUserItem, otherUserItem))

        val user1List = myLifeDao.getByUserId("user1")
        assertEquals(1, user1List.size)
        assertEquals("User1 Health", user1List[0].title)

        val guestList = myLifeDao.getByUserId(null)
        assertEquals(1, guestList.size)
        assertEquals("Guest Health", guestList[0].title)
    }

    @Test
    fun update_reordersItemsCorrectly() = runBlocking {
        val item1 = MyLife("img1", "user1", "Item1").apply { _id = "1"; weight = 0 }
        val item2 = MyLife("img2", "user1", "Item2").apply { _id = "2"; weight = 1 }
        myLifeDao.insertAll(listOf(item1, item2))

        item1.weight = 1
        item2.weight = 0
        myLifeDao.update(listOf(item1, item2))

        val list = myLifeDao.getByUserId("user1")
        assertEquals("Item2", list[0].title)
        assertEquals("Item1", list[1].title)
    }

    @Test
    fun getByIds_emptyInput_returnsEmptyList() = runBlocking {
        val result = myLifeDao.getByIds(emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun getByIds_belowChunkSize_returnsMatchingItems() = runBlocking {
        val item1 = MyLife("img1", "user1", "Item1").apply { _id = "id1" }
        val item2 = MyLife("img2", "user1", "Item2").apply { _id = "id2" }
        myLifeDao.insertAll(listOf(item1, item2))

        val result = myLifeDao.getByIds(listOf("id1", "id2"))
        assertEquals(2, result.size)
        assertEquals(setOf("id1", "id2"), result.map { it._id }.toSet())
    }

    @Test
    fun getByIds_aboveChunkSize_returnsMatchingItems() = runBlocking {
        val totalItems = 950
        val items = (1..totalItems).map { i ->
            MyLife("img$i", "user1", "Item$i").apply { _id = "id_$i" }
        }
        myLifeDao.insertAll(items)

        val queryIds = (1..totalItems).map { "id_$it" }
        val result = myLifeDao.getByIds(queryIds)

        assertEquals(totalItems, result.size)
        val returnedIds = result.map { it._id }
        assertEquals(totalItems, returnedIds.distinct().size)
        assertEquals(queryIds.toSet(), returnedIds.toSet())
    }
}
