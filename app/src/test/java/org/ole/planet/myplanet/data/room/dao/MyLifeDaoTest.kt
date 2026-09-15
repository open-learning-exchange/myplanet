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
}
