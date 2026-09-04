package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
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
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.HealthExaminationDao
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.DispatcherProvider

@ExperimentalCoroutinesApi
class HealthRepositoryImplTest {
    private lateinit var repository: HealthRepositoryImpl
    private val dispatcherProvider: DispatcherProvider = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockApiInterface: ApiInterface = mockk(relaxed = true)
    private val healthExaminationDao: HealthExaminationDao = mockk(relaxed = true)
    private val lazyUserRepository: dagger.Lazy<UserRepository> = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { lazyUserRepository.get() } returns userRepository
        every { dispatcherProvider.default } returns testDispatcher
        repository = HealthRepositoryImpl(
            mockApiInterface,
            dispatcherProvider,
            healthExaminationDao,
            lazyUserRepository,
            Gson()
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
        coEvery { userRepository.getUserById("user1") } returns UserEntity(id = "user1")
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
        coEvery { userRepository.getUserById("user1") } returns UserEntity(id = "user1", _id = "remote-user1")
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

        coVerify { healthExaminationDao.markUploaded(idToRevMap) }
    }

    @Test
    fun saveExamination_saves_objects_to_room() = testScope.runTest {
        val examination = HealthExamination()
        val pojo = HealthExamination()
        val user = UserEntity().apply { id = "user1" }

        repository.saveExamination(examination, pojo, user)
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.saveUser(match { it.id == "user1" }) }
        coVerify(exactly = 1) { healthExaminationDao.upsert(pojo) }
        coVerify(exactly = 1) { healthExaminationDao.upsert(examination) }
    }

    @Test
    fun saveExamination_handles_nulls() = testScope.runTest {
        val examination = HealthExamination()

        repository.saveExamination(examination, null, null)
        advanceUntilIdle()

        coVerify(exactly = 0) { userRepository.saveUser(any()) }
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
        val examination = HealthExamination().apply {
            conditions = "{\"Fever\": true, \"Cough\": false}"
        }

        val result = repository.getExaminationConditions(examination)
        advanceUntilIdle()

        assertEquals(2, result.size)
        assertEquals(true, result["Fever"])
        assertEquals(false, result["Cough"])
    }

    @Test
    fun getExaminationConditions_handles_empty_object_and_null_values() = testScope.runTest {
        // Empty object
        val emptyExamination = HealthExamination().apply {
            conditions = "{}"
        }
        val emptyResult = repository.getExaminationConditions(emptyExamination)
        advanceUntilIdle()
        assertTrue(emptyResult.isEmpty())

        // Null, string-boolean, and non-boolean values in JSON
        val nullValExamination = HealthExamination().apply {
            conditions = "{\"Fever\": true, \"Cough\": null, \"Headache\": \"true\", \"Chills\": \"false\"}"
        }
        val nullResult = repository.getExaminationConditions(nullValExamination)
        advanceUntilIdle()

        assertEquals(4, nullResult.size)
        assertEquals(true, nullResult["Fever"])
        assertEquals(false, nullResult["Cough"])
        assertEquals(true, nullResult["Headache"])
        assertEquals(false, nullResult["Chills"])
    }

    @Test
    fun bulkInsertFromSync_inserts_non_design_docs() = testScope.runTest {
        val jsonArray = JsonArray().apply {
            add(JsonObject().apply {
                add("doc", JsonObject().apply {
                    addProperty("_id", "exam1")
                    addProperty("_rev", "1-a")
                    addProperty("temperature", 98.6f)
                    add("conditions", JsonObject().apply { addProperty("Fever", true) })
                })
            })
            add(JsonObject().apply {
                add("doc", JsonObject().apply { addProperty("_id", "_design/skip") })
            })
        }

        repository.bulkInsertFromSync(jsonArray)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            healthExaminationDao.upsertAll(match {
                it.size == 1 &&
                    it.first()._id == "exam1" &&
                    it.first()._rev == "1-a" &&
                    it.first().conditions?.contains("Fever") == true
            })
        }
    }

    @Test
    fun uploadHealthData_successful_upload() = testScope.runTest {
        mockkObject(org.ole.planet.myplanet.utils.UrlUtils)
        every { org.ole.planet.myplanet.utils.UrlUtils.header } returns "mock-header"
        every { org.ole.planet.myplanet.utils.UrlUtils.getUrl() } returns "mock-url"

        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.isEmpty(any()) } answers {
            val str = firstArg<CharSequence?>()
            str.isNullOrEmpty()
        }

        val myHealths = listOf(HealthExamination().apply {
            _id = "exam1"
            userId = "exam1" // Serialize uses userId as _id in the request
        })

        val mockResponseObject = JsonObject().apply {
            addProperty("id", "exam1")
            addProperty("rev", "rev1")
        }
        val mockResponse = retrofit2.Response.success(mockResponseObject)
        coEvery { mockApiInterface.postDoc(any(), any(), any(), any()) } returns mockResponse

        val result = repository.uploadHealthData(myHealths)
        advanceUntilIdle()

        assertEquals(1, result.size)
        assertEquals("rev1", result["exam1"])

        unmockkObject(org.ole.planet.myplanet.utils.UrlUtils)
        io.mockk.unmockkStatic(android.text.TextUtils::class)
    }

    @Test
    fun uploadHealthData_failed_upload() = testScope.runTest {
        mockkObject(org.ole.planet.myplanet.utils.UrlUtils)
        every { org.ole.planet.myplanet.utils.UrlUtils.header } returns "mock-header"
        every { org.ole.planet.myplanet.utils.UrlUtils.getUrl() } returns "mock-url"

        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.isEmpty(any()) } answers {
            val str = firstArg<CharSequence?>()
            str.isNullOrEmpty()
        }

        val myHealths = listOf(HealthExamination().apply {
            _id = "exam1"
            userId = "user1"
        })

        // Mock a failure response, like a network error
        coEvery { mockApiInterface.postDoc(any(), any(), any(), any()) } throws RuntimeException("Network Error")

        val result = repository.uploadHealthData(myHealths)
        advanceUntilIdle()

        assertTrue(result.isEmpty())

        unmockkObject(org.ole.planet.myplanet.utils.UrlUtils)
        io.mockk.unmockkStatic(android.text.TextUtils::class)
    }

    @Test
    fun getByIdOrUserId_returns_examination() = testScope.runTest {
        val examination = HealthExamination().apply { _id = "exam1" }
        coEvery { healthExaminationDao.getByIdOrUserId("exam1") } returns examination

        val result = repository.getByIdOrUserId("exam1")
        advanceUntilIdle()

        assertEquals(examination, result)
    }

    @Test
    fun getByProfileId_returns_list() = testScope.runTest {
        val examination = HealthExamination().apply { _id = "exam1" }
        coEvery { healthExaminationDao.getByProfileId("profile1") } returns listOf(examination)

        val result = repository.getByProfileId("profile1")
        advanceUntilIdle()

        assertEquals(1, result.size)
        assertEquals(examination, result[0])
    }

    @Test
    fun upsert_saves_examination() = testScope.runTest {
        val examination = HealthExamination().apply { _id = "exam1" }
        repository.upsert(examination)
        advanceUntilIdle()

        coVerify { healthExaminationDao.upsert(examination) }
    }
}
