package org.ole.planet.myplanet.repository

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.PersonalDao
import org.ole.planet.myplanet.model.RealmMyPersonal

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalsRepositoryImplTest {

    private lateinit var personalDao: PersonalDao
    private lateinit var repository: PersonalsRepositoryImpl

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        personalDao = mockk(relaxed = true)
        val apiInterface = mockk<ApiInterface>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        repository = PersonalsRepositoryImpl(personalDao, apiInterface, context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `personalTitleExists returns true when title and user match`() = runTest {
        coEvery { personalDao.countByTitle("My Title", "user1") } returns 1

        val result = repository.personalTitleExists("My Title", "user1")

        assertTrue(result)
        coVerify { personalDao.countByTitle("My Title", "user1") }
    }

    @Test
    fun `personalTitleExists returns false when title does not exist`() = runTest {
        coEvery { personalDao.countByTitle("Missing", null) } returns 0

        val result = repository.personalTitleExists("Missing", null)

        assertFalse(result)
        coVerify { personalDao.countByTitle("Missing", null) }
    }

    @Test
    fun `savePersonalResource sets id and properties before saving`() = runTest {
        val savedObjectSlot = slot<RealmMyPersonal>()
        coEvery { personalDao.insert(capture(savedObjectSlot)) } returns Unit

        repository.savePersonalResource(
            title = "Test Title",
            userId = "user1",
            userName = "Test User",
            path = "/path/to/file",
            description = "Test Desc"
        )

        val captured = savedObjectSlot.captured
        assertEquals("Test Title", captured.title)
        assertEquals("user1", captured.userId)
        assertEquals("Test User", captured.userName)
        assertEquals("/path/to/file", captured.path)
        assertEquals("Test Desc", captured.description)
        assertTrue(captured.id.isNotEmpty())
        assertEquals(captured.id, captured._id)
    }

    @Test
    fun `getPersonalResources returns empty flow for null or blank userId`() = runTest {
        val resultNull = repository.getPersonalResources(null).first()
        assertTrue(resultNull.isEmpty())

        val resultBlank = repository.getPersonalResources("   ").first()
        assertTrue(resultBlank.isEmpty())
    }

    @Test
    fun `getPersonalResources returns flow of personals for valid userId`() = runTest {
        val expectedList = listOf(RealmMyPersonal())
        coEvery { personalDao.getByUserIdFlow("user1") } returns flowOf(expectedList)

        val result = repository.getPersonalResources("user1").first()

        assertEquals(expectedList, result)
        coVerify { personalDao.getByUserIdFlow("user1") }
    }

    @Test
    fun `deletePersonalResource deletes both _id and id`() = runTest {
        repository.deletePersonalResource("test-id")

        coVerify { personalDao.deleteByDocId("test-id") }
        coVerify { personalDao.deleteById("test-id") }
    }

    @Test
    fun `updatePersonalResource calls updater on matched _id and id`() = runTest {
        val personalByDocId = RealmMyPersonal().apply { title = "Old" }
        val personalById = RealmMyPersonal().apply { title = "Old" }
        coEvery { personalDao.findByDocId("test-id") } returns personalByDocId
        coEvery { personalDao.findById("test-id") } returns personalById

        var updateCount = 0
        repository.updatePersonalResource("test-id") { personal ->
            personal.title = "New Title"
            updateCount++
        }

        assertEquals(2, updateCount)
        assertEquals("New Title", personalByDocId.title)
        assertEquals("New Title", personalById.title)
        coVerify { personalDao.update(personalByDocId) }
        coVerify { personalDao.update(personalById) }
    }

    @Test
    fun `getPendingPersonalUploads queries correctly`() = runTest {
        coEvery { personalDao.getPendingUploads("user1") } returns listOf(RealmMyPersonal(), RealmMyPersonal())

        val results = repository.getPendingPersonalUploads("user1")

        assertEquals(2, results.size)
        coVerify { personalDao.getPendingUploads("user1") }
    }

    @Test
    fun `updatePersonalAfterSync updates fields properly`() = runTest {
        val personal = RealmMyPersonal()
        coEvery { personalDao.findById("test-id") } returns personal

        repository.updatePersonalAfterSync("test-id", "new-id", "rev-1")

        assertTrue(personal.isUploaded)
        assertEquals("new-id", personal._id)
        assertEquals("rev-1", personal._rev)
        coVerify { personalDao.update(personal) }
    }
}
