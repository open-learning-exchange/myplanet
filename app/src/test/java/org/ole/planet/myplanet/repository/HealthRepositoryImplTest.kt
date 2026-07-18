package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.HealthExaminationDao
import org.ole.planet.myplanet.data.room.dao.legacy.UserDao
import org.ole.planet.myplanet.data.room.entity.legacy.RoomUserEntity
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils

@ExperimentalCoroutinesApi
class HealthRepositoryImplTest {
    private lateinit var repository: HealthRepositoryImpl
    private val dispatcherProvider: DispatcherProvider = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockApiInterface: ApiInterface = mockk(relaxed = true)
    private val healthExaminationDao: HealthExaminationDao = mockk(relaxed = true)
    private val userDao: UserDao = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { dispatcherProvider.default } returns testDispatcher

        repository = HealthRepositoryImpl(
            mockApiInterface,
            dispatcherProvider,
            healthExaminationDao,
            userDao
        )
    }

    @After
    fun tearDown() {
        unmockkObject(AndroidDecrypter)
    }

    @Test
    fun initHealth_uses_dispatcherProvider_default() = testScope.runTest {
        mockkObject(AndroidDecrypter)
        every { AndroidDecrypter.generateKey() } returns "test_key"

        val result = repository.initHealth()
        advanceUntilIdle()
        assertNotNull(result)
        assertEquals("test_key", result.userKey)
        assertNotNull(result.profile)
        io.mockk.verify { dispatcherProvider.default }
    }

    @Test
    fun getHealthEntry_returns_user_and_examination() = testScope.runTest {
        val examination = HealthExamination().apply { _id = "user1" }
        coEvery { userDao.getById("user1") } returns RoomUserEntity(id = "user1")
        coEvery { healthExaminationDao.getByIdOrUserId("user1") } returns examination

        val result = repository.getHealthEntry("user1")
        advanceUntilIdle()

        assertEquals("user1", result.first?.id)
        assertEquals(examination, result.second)
    }

    @Test
    fun getHealthEntry_fallback_to_userId() = testScope.runTest {
        val examination = HealthExamination().apply {
            _id = "exam1"
            userId = "user1"
        }
        coEvery { userDao.getById("user1") } returns RoomUserEntity(id = "user1", _id = "remote-user1")
        coEvery { healthExaminationDao.getByIdOrUserId("user1") } returns examination

        val result = repository.getHealthEntry("user1")
        advanceUntilIdle()

        assertEquals("user1", result.first?.id)
        assertEquals("remote-user1", result.first?._id)
        assertEquals(examination, result.second)
    }

    @Test
    fun getExaminationById_returns_examination() = testScope.runTest {
        val examination = HealthExamination().apply { _id = "exam1" }

        coEvery { healthExaminationDao.getById("exam1") } returns examination

        val result = repository.getExaminationById("exam1")
        advanceUntilIdle()

        assertEquals(examination, result)
    }

    @Test
    fun getUpdatedHealthExaminations_returns_list() = testScope.runTest {
        val examination = HealthExamination().apply {
            _id = "exam1"
            isUpdated = true
        }
        coEvery { healthExaminationDao.getUpdated() } returns listOf(examination)

        val result = repository.getUpdatedHealthExaminations()
        advanceUntilIdle()

        assertEquals(1, result.size)
        assertEquals(examination, result[0])
    }

    @Test
    fun getUpdatedHealthForUser_returns_list() = testScope.runTest {
        val examination = HealthExamination().apply {
            _id = "exam1"
            isUpdated = true
            userId = "user1"
        }
        coEvery { healthExaminationDao.getUpdatedForUser("user1") } returns listOf(examination)

        val result = repository.getUpdatedHealthForUser("user1")
        advanceUntilIdle()

        assertEquals(1, result.size)
        assertEquals(examination, result[0])
    }

    @Test
    fun markHealthExaminationsUploaded_updates_revisions() = testScope.runTest {
        val idToRevMap = mapOf("exam1" to "rev1", "exam2" to "rev2")

        repository.markHealthExaminationsUploaded(idToRevMap)
        advanceUntilIdle()

        coVerify { healthExaminationDao.markUploaded("exam1", "rev1") }
        coVerify { healthExaminationDao.markUploaded("exam2", "rev2") }
    }

    @Test
    fun saveExamination_saves_objects_to_room() = testScope.runTest {
        val examination = HealthExamination()
        val pojo = HealthExamination()
        val user = org.ole.planet.myplanet.model.RealmUser().apply { id = "user1" }

        repository.saveExamination(examination, pojo, user)
        advanceUntilIdle()

        coVerify(exactly = 1) { userDao.upsert(match { it.id == "user1" }) }
        coVerify(exactly = 1) { healthExaminationDao.upsert(pojo) }
        coVerify(exactly = 1) { healthExaminationDao.upsert(examination) }
    }

    @Test
    fun saveExamination_handles_nulls() = testScope.runTest {
        val examination = HealthExamination()

        repository.saveExamination(examination, null, null)
        advanceUntilIdle()

        coVerify(exactly = 0) { userDao.upsert(any()) }
        coVerify(exactly = 1) { healthExaminationDao.upsert(examination) }
    }

    @Test
    fun updateExaminationUserId_updates_id() = testScope.runTest {
        repository.updateExaminationUserId("exam1", "new_user")
        advanceUntilIdle()

        coVerify { healthExaminationDao.updateUserId("exam1", "new_user") }
    }

    @Test
    fun getExaminationConditions_returns_empty_map_for_null_examination() = testScope.runTest {
        val result = repository.getExaminationConditions(null)
        advanceUntilIdle()
        assertTrue(result.isEmpty())
    }

    @Test
    fun getExaminationConditions_returns_map_for_valid_json() = testScope.runTest {
        mockkObject(JsonUtils)
        val examination = HealthExamination()
        examination.conditions = "{"Fever": true, "Cough": false}"

        val jsonObject = JsonObject()
        jsonObject.addProperty("Fever", true)
        jsonObject.addProperty("Cough", false)

        every { JsonUtils.gson.fromJson(examination.conditions, JsonObject::class.java) } returns jsonObject
        every { JsonUtils.getBoolean("Fever", jsonObject) } returns true
        every { JsonUtils.getBoolean("Cough", jsonObject) } returns false

        val result = repository.getExaminationConditions(examination)
        advanceUntilIdle()

        assertEquals(2, result.size)
        assertTrue(result["Fever"] == true)
        assertFalse(result["Cough"] == true)
    }

    @Test
    fun bulkInsertFromSync_inserts_non_design_docs() = testScope.runTest {
        mockkObject(HealthExamination.Companion)
        val jsonArray = JsonArray().apply {
            add(JsonObject().apply {
                add("doc", JsonObject().apply { addProperty("_id", "exam1") })
            })
            add(JsonObject().apply {
                add("doc", JsonObject().apply { addProperty("_id", "_design/skip") })
            })
        }
        val mapped = HealthExamination().apply { _id = "exam1" }
        every { HealthExamination.fromJson(any()) } returns mapped

        repository.bulkInsertFromSync(jsonArray)
        advanceUntilIdle()

        coVerify(exactly = 1) { healthExaminationDao.upsertAll(match { it.size == 1 && it.first()._id == "exam1" }) }
    }
}
