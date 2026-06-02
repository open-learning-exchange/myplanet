package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.slot
import io.mockk.verify
import io.mockk.coVerify
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.data.applyEqualTo
import org.ole.planet.myplanet.data.findCopyByField
import org.ole.planet.myplanet.data.queryList

@ExperimentalCoroutinesApi
class HealthRepositoryImplTest {
    private lateinit var repository: HealthRepositoryImpl
    private val dispatcherProvider: DispatcherProvider = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val databaseService: DatabaseService = mockk(relaxed = true)
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

        every { realm.where(RealmHealthExamination::class.java) } returns healthQuery
        every { realm.where(RealmUser::class.java) } returns userQuery

        every { healthQuery.equalTo(any<String>(), any<String>()) } returns healthQuery
        every { healthQuery.equalTo(any<String>(), any<Boolean>()) } returns healthQuery
        every { healthQuery.notEqualTo(any<String>(), any<String>()) } returns healthQuery
        every { healthQuery.`in`(any<String>(), any<Array<String>>()) } returns healthQuery
        every { healthQuery.findAll() } returns healthResults

        every { userQuery.equalTo(any<String>(), any<String>()) } returns userQuery

        every { healthResults.iterator() } answers { healthResultsIterable.toMutableList().iterator() }
        every { healthResults.isValid } returns true

        every { realm.copyFromRealm(any<Iterable<RealmHealthExamination>>()) } answers {
            val list = arg<Iterable<RealmHealthExamination>>(0)
            list.toList()
        }
        every { realm.copyFromRealm(any<RealmHealthExamination>()) } answers { arg<RealmHealthExamination>(0) }
        every { realm.copyFromRealm(any<RealmUser>()) } answers { arg<RealmUser>(0) }

        mockkStatic("org.ole.planet.myplanet.data.DatabaseServiceKt")

        repository = HealthRepositoryImpl(
            databaseService,
            UnconfinedTestDispatcher(),
            dispatcherProvider
        )
    }

    @After
    fun tearDown() {
        unmockkObject(AndroidDecrypter)
        unmockkStatic("org.ole.planet.myplanet.data.DatabaseServiceKt")
        unmockkStatic(JsonUtils::class)
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

        every { realm.findCopyByField(RealmUser::class.java, "id", "user1") } returns user
        every { realm.findCopyByField(RealmHealthExamination::class.java, "_id", "user1") } returns examination

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

        every { realm.findCopyByField(RealmUser::class.java, "id", "user1") } returns user
        every { realm.findCopyByField(RealmHealthExamination::class.java, "_id", "user1") } returns null
        every { realm.findCopyByField(RealmHealthExamination::class.java, "userId", "user1") } returns examination

        val result = repository.getHealthEntry("user1")
        advanceUntilIdle()

        assertEquals(user, result.first)
        assertEquals(examination, result.second)
    }

    @Test
    fun getExaminationById_returns_examination() = testScope.runTest {
        val examination = RealmHealthExamination()
        examination._id = "exam1"

        every { realm.findCopyByField(RealmHealthExamination::class.java, "_id", "exam1") } returns examination

        val result = repository.getExaminationById("exam1")
        advanceUntilIdle()

        assertEquals(examination, result)
    }

    @Test
    fun getUpdatedHealthExaminations_returns_list() = testScope.runTest {
        val examination = RealmHealthExamination()
        examination._id = "exam1"
        examination.isUpdated = true

        healthResultsIterable.clear()
        healthResultsIterable.add(examination)

        val builderSlot = slot<(RealmQuery<RealmHealthExamination>) -> Unit>()
        every { realm.queryList(RealmHealthExamination::class.java, capture(builderSlot)) } returns healthResultsIterable

        val result = repository.getUpdatedHealthExaminations()
        builderSlot.captured.invoke(healthQuery)
        verify { healthQuery.equalTo("isUpdated", true) }
        verify { healthQuery.notEqualTo("userId", "") }
        advanceUntilIdle()

        assertEquals(1, result.size)
        assertEquals(examination, result[0])
    }

    @Test
    fun getUpdatedHealthForUser_returns_list() = testScope.runTest {
        val examination = RealmHealthExamination()
        examination._id = "exam1"
        examination.isUpdated = true
        examination.userId = "user1"

        healthResultsIterable.clear()
        healthResultsIterable.add(examination)

        val builderSlot = slot<(RealmQuery<RealmHealthExamination>) -> Unit>()
        every { realm.queryList(RealmHealthExamination::class.java, capture(builderSlot)) } returns healthResultsIterable

        val result = repository.getUpdatedHealthForUser("user1")
        builderSlot.captured.invoke(healthQuery)
        verify { healthQuery.equalTo("isUpdated", true) }
        verify { healthQuery.equalTo("userId", "user1") }
        advanceUntilIdle()

        assertEquals(1, result.size)
        assertEquals(examination, result[0])
    }

    @Test
    fun markHealthExaminationsUploaded_updates_revisions() = testScope.runTest {
        val idToRevMap = mapOf("exam1" to "rev1", "exam2" to "rev2")
        val exam1 = RealmHealthExamination()
        exam1._id = "exam1"
        val exam2 = RealmHealthExamination()
        exam2._id = "exam2"

        healthResultsIterable.clear()
        healthResultsIterable.add(exam1)
        healthResultsIterable.add(exam2)

        every { healthQuery.`in`("_id", any<Array<String>>()) } returns healthQuery

        repository.markHealthExaminationsUploaded(idToRevMap)
        advanceUntilIdle()

        verify { healthQuery.`in`(eq("_id"), match<Array<String>> { it.toSet() == setOf("exam1", "exam2") }) }
        assertEquals("rev1", exam1._rev)
        assertEquals(false, exam1.isUpdated)
        assertEquals("rev2", exam2._rev)
        assertEquals(false, exam2.isUpdated)
    }

    @Test
    fun saveExamination_saves_objects_to_realm() = testScope.runTest {
        val examination = RealmHealthExamination()
        val pojo = RealmHealthExamination()
        val user = RealmUser()

        every { realm.copyToRealmOrUpdate(any<RealmHealthExamination>()) } returns examination
        every { realm.copyToRealmOrUpdate(any<RealmUser>()) } returns user

        repository.saveExamination(examination, pojo, user)
        advanceUntilIdle()

        verify(exactly = 1) { realm.copyToRealmOrUpdate(user) }
        verify(exactly = 1) { realm.copyToRealmOrUpdate(pojo) }
        verify(exactly = 1) { realm.copyToRealmOrUpdate(examination) }
    }

    @Test
    fun saveExamination_handles_nulls() = testScope.runTest {
        val examination = RealmHealthExamination()

        every { realm.copyToRealmOrUpdate(any<RealmHealthExamination>()) } returns examination

        repository.saveExamination(examination, null, null)
        advanceUntilIdle()

        verify(exactly = 0) { realm.copyToRealmOrUpdate(any<RealmUser>()) }
        verify(exactly = 1) { realm.copyToRealmOrUpdate(examination) }
    }

    @Test
    fun updateExaminationUserId_updates_id() = testScope.runTest {
        val examination = RealmHealthExamination()
        examination._id = "exam1"
        examination.userId = "old_user"

        every { healthQuery.applyEqualTo("_id", "exam1") } returns healthQuery
        every { healthQuery.findFirst() } returns examination

        repository.updateExaminationUserId("exam1", "new_user")
        advanceUntilIdle()

        assertEquals("new_user", examination.userId)
    }

    @Test
    fun bulkInsertFromSync_inserts_items() = testScope.runTest {
        mockkStatic(JsonUtils::class)
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

        every { RealmHealthExamination.insert(realm, doc1) } returns Unit

        repository.bulkInsertFromSync(realm, jsonArray)
        advanceUntilIdle()

        verify(exactly = 1) { RealmHealthExamination.insert(realm, doc1) }
        verify(exactly = 0) { RealmHealthExamination.insert(realm, doc2) }
    }
}
