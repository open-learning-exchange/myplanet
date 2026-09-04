package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.ResourceActivity
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ResourceActivityDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ResourceActivityDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.resourceActivityDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun createActivity(
        userName: String,
        type: String,
        resId: String,
        titleName: String?
    ): ResourceActivity {
        return ResourceActivity().apply {
            id = UUID.randomUUID().toString()
            user = userName
            this.type = type
            resourceId = resId
            title = titleName
            time = System.currentTimeMillis()
        }
    }

    @Test
    fun getMostOpenedResource_returnsNullWhenTableEmpty() = runBlocking {
        val result = dao.getMostOpenedResource("john", "pdf")
        assertNull(result)
    }

    @Test
    fun getMostOpenedResource_returnsSingleWinner() = runBlocking {
        dao.insert(createActivity("john", "pdf", "res1", "Resource One"))
        dao.insert(createActivity("john", "pdf", "res1", "Resource One"))
        dao.insert(createActivity("john", "pdf", "res2", "Resource Two"))

        val result = dao.getMostOpenedResource("john", "pdf")
        assertNotNull(result)
        assertEquals("Resource One", result?.title)
        assertEquals(2, result?.openCount)
    }

    @Test
    fun getMostOpenedResource_breaksTieDeterministicallyByTitleAsc() = runBlocking {
        // Both resA and resB opened 2 times. "Alpha Resource" comes before "Beta Resource" alphabetically.
        dao.insert(createActivity("john", "pdf", "resB", "Beta Resource"))
        dao.insert(createActivity("john", "pdf", "resB", "Beta Resource"))
        dao.insert(createActivity("john", "pdf", "resA", "Alpha Resource"))
        dao.insert(createActivity("john", "pdf", "resA", "Alpha Resource"))

        val result = dao.getMostOpenedResource("john", "pdf")
        assertNotNull(result)
        assertEquals("Alpha Resource", result?.title)
        assertEquals(2, result?.openCount)
    }

    @Test
    fun getMostOpenedResource_excludesNullAndBlankTitles() = runBlocking {
        dao.insert(createActivity("john", "pdf", "resNull", null))
        dao.insert(createActivity("john", "pdf", "resNull", null))
        dao.insert(createActivity("john", "pdf", "resBlank", "   "))
        dao.insert(createActivity("john", "pdf", "resValid", "Valid Resource"))

        val result = dao.getMostOpenedResource("john", "pdf")
        assertNotNull(result)
        assertEquals("Valid Resource", result?.title)
        assertEquals(1, result?.openCount)
    }
}
