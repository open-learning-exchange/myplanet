package org.ole.planet.myplanet.datamanager

import android.content.Context
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.firstArg
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.log.LogLevel
import io.realm.log.RealmLog
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

open class TestModel(var id: String = "") : RealmObject()

class DatabaseServiceTest {
    private lateinit var realm: Realm
    private lateinit var service: DatabaseService

    @Before
    fun setUp() {
        mockkStatic(Realm::class)
        mockkStatic(RealmLog::class)
        realm = mockk(relaxed = true)
        every { Realm.init(any()) } just Runs
        every { RealmLog.setLevel(any<LogLevel>()) } just Runs
        every { Realm.setDefaultConfiguration(any()) } just Runs
        every { Realm.getDefaultInstance() } returns realm
        every { realm.close() } just Runs
        val context = mockk<Context>()
        service = DatabaseService(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(Realm::class)
        unmockkStatic(RealmLog::class)
        clearAllMocks()
    }

    @Test
    fun executeTransactionAsync_closedRealm() = runBlocking {
        every { realm.isClosed } returns true

        service.executeTransactionAsync { }

        verify(exactly = 0) { realm.executeTransaction(any()) }
    }

    @Test
    fun executeTransactionAsync_swallowKnownIllegalState() = runBlocking {
        every { realm.isClosed } returns false
        every { realm.executeTransaction(any()) } throws IllegalStateException("non-existing write transaction")

        service.executeTransactionAsync { }

        verify { realm.executeTransaction(any()) }
    }

    @Test
    fun executeTransactionAsync_otherIllegalStateThrows() {
        every { realm.isClosed } returns false
        every { realm.executeTransaction(any()) } throws IllegalStateException("other")

        assertThrows(IllegalStateException::class.java) {
            runBlocking { service.executeTransactionAsync { } }
        }
    }

    @Test
    fun executeTransactionWithResultAsync_closedRealmReturnsNull() = runBlocking {
        every { realm.isClosed } returns true

        val result = service.executeTransactionWithResultAsync { 42 }

        assertNull(result)
        verify(exactly = 0) { realm.executeTransaction(any()) }
    }

    @Test
    fun executeTransactionWithResultAsync_successReturnsResult() = runBlocking {
        every { realm.isClosed } returns false
        every { realm.executeTransaction(any()) } answers {
            val t = firstArg<Realm.Transaction>()
            t.execute(realm)
        }

        val result = service.executeTransactionWithResultAsync { 42 }

        assertEquals(42, result)
    }

    @Test
    fun executeTransactionWithResultAsync_swallowKnownIllegalState() = runBlocking {
        every { realm.isClosed } returns false
        every { realm.executeTransaction(any()) } throws IllegalStateException("not currently in a transaction")

        val result = service.executeTransactionWithResultAsync { 42 }

        assertNull(result)
    }

    @Test
    fun executeTransactionWithResultAsync_otherIllegalStateThrows() {
        every { realm.isClosed } returns false
        every { realm.executeTransaction(any()) } throws IllegalStateException("other")

        assertThrows(IllegalStateException::class.java) {
            runBlocking { service.executeTransactionWithResultAsync { 42 } }
        }
    }

    @Test
    fun queryList_returnsCopiedResults() {
        val query = mockk<RealmQuery<TestModel>>(relaxed = true)
        val results = mockk<RealmResults<TestModel>>()
        val expected = listOf(TestModel("1"))
        every { realm.where(TestModel::class.java) } returns query
        every { query.findAll() } returns results
        every { realm.copyFromRealm(results) } returns expected

        val actual = realm.queryList(TestModel::class.java) {
            equalTo("id", "1")
        }

        assertEquals(expected, actual)
        verify { query.equalTo("id", "1") }
    }

    @Test
    fun findCopyByField_returnsCopiedObject() {
        val query = mockk<RealmQuery<TestModel>>(relaxed = true)
        val found = TestModel("1")
        val copied = TestModel("1")
        every { realm.where(TestModel::class.java) } returns query
        every { query.equalTo("id", "1") } returns query
        every { query.findFirst() } returns found
        every { realm.copyFromRealm(found) } returns copied

        val result = realm.findCopyByField(TestModel::class.java, "id", "1")

        assertEquals(copied, result)
        verify { query.equalTo("id", "1") }
    }
}

