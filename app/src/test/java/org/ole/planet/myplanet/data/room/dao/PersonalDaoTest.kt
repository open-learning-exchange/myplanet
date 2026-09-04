package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.api.ApiInterface
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
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        personalDao = database.personalDao()

        val apiInterface = mockk<ApiInterface>(relaxed = true)
        val uploadRepository = mockk<UploadRepository>(relaxed = true)
        val deviceNameProvider = mockk<DeviceNameProvider>(relaxed = true)

        repository = PersonalsRepositoryImpl(personalDao, apiInterface, uploadRepository, deviceNameProvider)
    }

    @After
    fun teardown() {
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
