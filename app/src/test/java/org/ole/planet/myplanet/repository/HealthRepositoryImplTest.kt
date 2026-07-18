package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.HealthExaminationDao
import org.ole.planet.myplanet.data.applyEqualTo
import org.ole.planet.myplanet.data.findCopyByField
import org.ole.planet.myplanet.data.queryList
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils

@ExperimentalCoroutinesApi
class HealthRepositoryImplTest {
    private lateinit var repository: HealthRepositoryImpl
    private val dispatcherProvider: DispatcherProvider = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val mockApiInterface: ApiInterface = mockk(relaxed = true)
    private val healthExaminationDao: HealthExaminationDao = mockk(relaxed = true)
    private val realm: Realm = mockk(relaxed = true)
    private val healthQuery: RealmQuery<RealmHealthExamination> = mockk(relaxed = true)
    private val userQuery: RealmQuery<RealmUser> = mockk(relaxed = true)
    private val healthResults: RealmResults<RealmHealthExamination> = mockk(relaxed = true)
    private val healthResultsIterable = mutableListOf<RealmHealthExamination>()

    @Before
    fun setUp() {
        every { dispatcherProvider.default } returns testDispatcher

        every { databaseService.createManagedRealmInstance() } returns realm
        coEvery { databaseService.withRealmAsync<Any?>(any()) } answers {
            val operation = firstArg<(Realm) -> Any?>()
            operation(realm)
        }
        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val operation = firstArg<(Realm) -> Unit>()
            operation(realm)
        }

        every { realm.where(RealmUser::class.java) } returns userQuery

        every { healthQuery.equalTo(any<String>(), any<String>()) } returns healthQuery
        every { healthQuery.equalTo(any<String>(), any<Boolean>()) } returns healthQuery
        every { healthQuery.notEqualTo(any<String>(), any<String>()) } returns healthQuery
        every { healthQuery.`in`(any<String>(), any<Array<String>>()) } returns healthQuery
        every { healthQuery.findAll() } returns healthResults

        every { userQuery.equalTo(any<String>(), any<String>()) } returns userQuery


        every { realm.copyFromRealm(any<RealmUser>()) } answers { arg<RealmUser>(0) }

        mockkStatic("org.ole.planet.myplanet.data.DatabaseServiceKt")

        repository = HealthRepositoryImpl(
            mockApiInterface,
            databaseService,
            UnconfinedTestDispatcher(),
            dispatcherProvider,
            healthExaminationDao
        )
    }

    @After
    fun tearDown() {
        unmockkObject(AndroidDecrypter)
        unmockkStatic("org.ole.planet.myplanet.data.DatabaseServiceKt")
        unmockkObject(JsonUtils)
        unmockkObject(RealmHealthExamination.Companion)
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
        verify { dispatcherProvider.default }
    }

    @Test
    fun getHealthEntry_returns_user_and_examination() = testScope.runTest {
        val user = RealmUser()
        user.id = "user1"
        val examination = RealmHealthExamination()
        examination._id = "user1"

        every { realm.findCopyByField(RealmUser::class.java, "_id", "user1") } returns null
        every { realm.findCopyByField(RealmUser::class.java, "id", "user1") } returns user
        coEvery { healthExaminationDao.getByIdOrUserId("user1") } returns examination

        val result = repository.getHealthEntry("user1")
        advanceUntilIdle()

        assertEquals(user, result.first)
        assertEquals(examination, result.second)
    }

    @Test
    fun getHealthEntry_fallback_to_userId() = testScope.runTest {
        val user = RealmUser()
        user.id = "user1"
        val examination = RealmHealthExamination()
        examination._id = "exam1"
        examination.userId = "user1"

        every { realm.findCopyByField(RealmUser::class.java, "_id", "user1") } returns null
        every { realm.findCopyByField(RealmUser::class.java, "id", "user1") } returns user
        coEvery { healthExaminationDao.getByIdOrUserId("user1") } returns examination

        val result = repository.getHealthEntry("user1")
        advanceUntilIdle()

        assertEquals(user, result.first)
        assertEquals(examination, result.second)
    }

    @Test
    fun getExaminationById_returns_examination() = testScope.runTest {
        val examination = RealmHealthExamination()
        examination._id = "exam1"

        coEvery { healthExaminationDao.getById("exam1") } returns examination

        val result = repository.getExaminationById("exam1")
        advanceUntilIdle()

        assertEquals(examination, result)
    }

    @Test
    fun getUpdatedHealthExaminations_returns_list() = testScope.runTest {
        val examination = RealmHealthExamination().apply {
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
        val examination = RealmHealthExamination().apply {
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
    fun saveExamination_saves_objects_to_realm() = testScope.runTest {
        val examination = RealmHealthExamination()
        val pojo = RealmHealthExamination()
        val user = RealmUser()

        repository.saveExamination(examination, pojo, user)
        advanceUntilIdle()

        coVerify(exactly = 1) { healthExaminationDao.upsert(pojo) }
        coVerify(exactly = 1) { healthExaminationDao.upsert(examination) }
    }

    @Test
    fun saveExamination_handles_nulls() = testScope.runTest {
        val examination = RealmHealthExamination()

        repository.saveExamination(examination, null, null)
        advanceUntilIdle()

        coVerify(exactly = 1) { healthExaminationDao.upsert(examination) }
    }

    @Test
    fun updateExaminationUserId_updates_id() = testScope.runTest {
        val examination = RealmHealthExamination()
        examination._id = "exam1"
        examination.userId = "old_user"

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
        val examination = RealmHealthExamination()
        examination.conditions = "{\"Fever\": true, \"Cough\": false}"

        val jsonObject = JsonObject()
        jsonObject.addProperty("Fever", true)
        jsonObject.addProperty("Cough", false)

        every { JsonUtils.getBoolean("Fever", jsonObject) } returns true
        every { JsonUtils.getBoolean("Cough", jsonObject) } returns false

        val result = repository.getExaminationConditions(examination)
        advanceUntilIdle()

        assertEquals(2, result.size)
        assertEquals(true, result["Fever"])
        assertEquals(false, result["Cough"])
    }

    @Test
    fun getExaminationConditions_returns_empty_map_for_malformed_json() = testScope.runTest {
        val examination = RealmHealthExamination()
        examination.conditions = "malformed json"

        val result = repository.getExaminationConditions(examination)
        advanceUntilIdle()

        assertTrue(result.isEmpty())
    }

    @Test
    fun bulkInsertFromSync_inserts_items() = testScope.runTest {
        mockkObject(JsonUtils)
        mockkObject(RealmHealthExamination.Companion)

        val jsonArray = JsonArray()
        val doc1 = JsonObject()
        doc1.addProperty("_id", "exam1")
        val item1 = JsonObject()
        item1.add("doc", doc1)

        val doc2 = JsonObject()
        doc2.addProperty("_id", "_design/doc")
        val item2 = JsonObject()
        item2.add("doc", doc2)

        jsonArray.add(item1)
        jsonArray.add(item2)

        every { JsonUtils.getJsonObject("doc", item1) } returns doc1
        every { JsonUtils.getString("_id", doc1) } returns "exam1"

        every { JsonUtils.getJsonObject("doc", item2) } returns doc2
        every { JsonUtils.getString("_id", doc2) } returns "_design/doc"

        val detachedExamination = mockk<RealmHealthExamination>()
        every { RealmHealthExamination.fromJson(doc1) } returns detachedExamination
        repository.bulkInsertFromSync(jsonArray)
        advanceUntilIdle()

        verify(exactly = 1) { RealmHealthExamination.fromJson(doc1) }
        verify(exactly = 0) { RealmHealthExamination.fromJson(doc2) }
        coVerify(exactly = 1) { healthExaminationDao.upsertAll(listOf(detachedExamination)) }
    }
}
