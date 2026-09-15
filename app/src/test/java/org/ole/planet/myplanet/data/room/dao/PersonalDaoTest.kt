package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.Personal
import org.ole.planet.myplanet.repository.PersonalsRepositoryImpl
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.utils.DeviceNameProvider

@RunWith(AndroidJUnit4::class)
class PersonalDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var personalDao: PersonalDao
    private lateinit var repository: PersonalsRepositoryImpl

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        personalDao = database.personalDao()
        repository = PersonalsRepositoryImpl(
            personalDao,
            mockk<UploadRepository>(relaxed = true),
            mockk<DeviceNameProvider>(relaxed = true)
        )
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun createPersonal(
        id: String,
        userId: String = "user1",
        title: String,
        date: Long
    ) = Personal().apply {
        this.id = id
        this._id = id
        this.userId = userId
        this.title = title
        this.date = date
    }

    @Test
    fun updateFields_updatesTitleLeavesOtherFieldsUntouched() = runBlocking {
        val personal = Personal().apply {
            id = "local-1"
            _id = "doc-1"
            _rev = "1-rev"
            isUploaded = true
            title = "Original Title"
            description = "Original Description"
            path = "/path/to/file.txt"
        }
        personalDao.insert(personal)

        personalDao.updateFields("doc-1", "Updated Title", null)

        val updated = personalDao.findByDocId("doc-1")
        assertNotNull(updated)
        assertEquals("Updated Title", updated?.title)
        assertEquals("Original Description", updated?.description)
        assertEquals("/path/to/file.txt", updated?.path)
        assertEquals("1-rev", updated?._rev)
        assertEquals(true, updated?.isUploaded)
    }

    @Test
    fun updateFields_resolvesByIdAndDocId() = runBlocking {
        val p1 = Personal().apply {
            id = "local-1"
            _id = "doc-1"
            title = "Title 1"
            description = "Desc 1"
        }
        val p2 = Personal().apply {
            id = "local-2"
            _id = null
            title = "Title 2"
            description = "Desc 2"
        }
        personalDao.insert(p1)
        personalDao.insert(p2)

        // Update p1 by _id
        personalDao.updateFields("doc-1", "New Title 1", "New Desc 1")
        // Update p2 by id
        personalDao.updateFields("local-2", "New Title 2", "New Desc 2")

        val updated1 = personalDao.findByDocId("doc-1")
        assertEquals("New Title 1", updated1?.title)
        assertEquals("New Desc 1", updated1?.description)

        val updated2 = personalDao.findById("local-2")
        assertEquals("New Title 2", updated2?.title)
        assertEquals("New Desc 2", updated2?.description)
    }

    @Test
    fun updateFields_nullValuesLeaveColumnsUnchanged() = runBlocking {
        val personal = Personal().apply {
            id = "local-1"
            _id = "doc-1"
            title = "Original Title"
            description = "Original Description"
        }
        personalDao.insert(personal)

        personalDao.updateFields("doc-1", null, null)

        val updated = personalDao.findByDocId("doc-1")
        assertEquals("Original Title", updated?.title)
        assertEquals("Original Description", updated?.description)
    }

    @Test
    fun `getPersonalResources flow sorts newer entries first`() = runBlocking {
        personalDao.insert(createPersonal(id = "p1", title = "Older Document", date = 1000L))
        personalDao.insert(createPersonal(id = "p2", title = "Newer Document", date = 3000L))
        personalDao.insert(createPersonal(id = "p3", title = "Middle Document", date = 2000L))

        val result = repository.getPersonalResources("user1").first()

        assertEquals(listOf("p2", "p3", "p1"), result.map { it.id })
    }

    @Test
    fun `getPersonalResources flow sorts same date entries by title case insensitively`() = runBlocking {
        val sameDate = 5000L
        personalDao.insert(createPersonal(id = "p1", title = "charlie", date = sameDate))
        personalDao.insert(createPersonal(id = "p2", title = "Apple", date = sameDate))
        personalDao.insert(createPersonal(id = "p3", title = "banana", date = sameDate))
        personalDao.insert(createPersonal(id = "p4", title = "DELTA", date = sameDate))

        val result = repository.getPersonalResources("user1").first()

        assertEquals(listOf("p2", "p3", "p1", "p4"), result.map { it.id })
        assertEquals(listOf("Apple", "banana", "charlie", "DELTA"), result.map { it.title })
    }
}
