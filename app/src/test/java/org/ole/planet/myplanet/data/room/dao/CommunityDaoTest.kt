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
import org.ole.planet.myplanet.model.Community

@RunWith(AndroidJUnit4::class)
class CommunityDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var communityDao: CommunityDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        communityDao = database.communityDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun createCommunity(id: String, name: String, weight: Int): Community {
        return Community().apply {
            this.id = id
            this.name = name
            this.weight = weight
        }
    }

    @Test
    fun getAllSorted_returnsNonEmptyNamesOrderedByWeightAscending() = runBlocking {
        val communities = listOf(
            createCommunity("1", "b", 5),
            createCommunity("2", "learning", 0),
            createCommunity("3", "", 1)
        )
        communityDao.insertAll(communities)

        val result = communityDao.getAllSorted()
        assertEquals(listOf("learning", "b"), result.map { it.name })
    }

    @Test
    fun replaceAll_replacesPreviousRows() = runBlocking {
        val initialCommunities = listOf(
            createCommunity("1", "b", 5),
            createCommunity("2", "learning", 0),
            createCommunity("3", "", 1)
        )
        communityDao.insertAll(initialCommunities)
        assertEquals(listOf("learning", "b"), communityDao.getAllSorted().map { it.name })

        val replacementCommunities = listOf(
            createCommunity("4", "alpha", 2),
            createCommunity("5", "beta", 1)
        )
        communityDao.replaceAll(replacementCommunities)

        val result = communityDao.getAllSorted()
        assertEquals(listOf("beta", "alpha"), result.map { it.name })
    }
}
