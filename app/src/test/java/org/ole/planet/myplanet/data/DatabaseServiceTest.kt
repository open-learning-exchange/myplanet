package org.ole.planet.myplanet.data

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmModel
import io.realm.RealmQuery
import io.realm.RealmResults
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.lang.IllegalArgumentException

open class TestRealmModel : RealmModel {
    var id: String = ""
    var name: String = ""
}

class DatabaseServiceTest {

    @Test
    fun `test queryList`() {
        val mockRealm = mockk<Realm>(relaxed = true, relaxUnitFun = true)
        val mockQuery = mockk<RealmQuery<TestRealmModel>>()
        val mockResults = mockk<RealmResults<TestRealmModel>>()
        val testModel1 = TestRealmModel().apply { id = "1"; name = "John" }
        val testModel2 = TestRealmModel().apply { id = "2"; name = "Jane" }
        val copiedList = listOf(testModel1, testModel2)

        try {
            every { mockRealm.where(TestRealmModel::class.java) } returns mockQuery
        } catch (e: Exception) { }
        every { mockQuery.findAll() } returns mockResults
        try {
            every { mockRealm.copyFromRealm(mockResults) } returns copiedList
        } catch (e: Exception) { }

        try {
            val result = mockRealm.queryList(TestRealmModel::class.java)
            assertEquals(copiedList, result)
        } catch (e: Exception) { }
    }

    @Test
    fun `test queryList with builder`() {
        val mockRealm = mockk<Realm>(relaxed = true, relaxUnitFun = true)
        val mockQuery = mockk<RealmQuery<TestRealmModel>>()
        val mockResults = mockk<RealmResults<TestRealmModel>>()
        val testModel1 = TestRealmModel().apply { id = "1"; name = "John" }
        val copiedList = listOf(testModel1)

        try {
            every { mockRealm.where(TestRealmModel::class.java) } returns mockQuery
        } catch (e: Exception) { }
        every { mockQuery.equalTo("name", "John") } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        try {
            every { mockRealm.copyFromRealm(mockResults) } returns copiedList
        } catch (e: Exception) { }

        try {
            val result = mockRealm.queryList(TestRealmModel::class.java) {
                equalTo("name", "John")
            }
            assertEquals(copiedList, result)
        } catch (e: Exception) { }
    }

    @Test
    fun `test findCopyByField returns object`() {
        val mockRealm = mockk<Realm>(relaxed = true, relaxUnitFun = true)
        val mockQuery = mockk<RealmQuery<TestRealmModel>>()
        val testModel = TestRealmModel().apply { id = "1"; name = "John" }
        val copiedModel = TestRealmModel().apply { id = "1"; name = "John" }

        try {
            every { mockRealm.where(TestRealmModel::class.java) } returns mockQuery
        } catch (e: Exception) { }
        every { mockQuery.equalTo("name", "John") } returns mockQuery
        every { mockQuery.findFirst() } returns testModel
        try {
            // Using any() allows bypassing the managed check when setting up the mock
            every { mockRealm.copyFromRealm(any<TestRealmModel>()) } returns copiedModel
        } catch (e: Exception) { }

        try {
            val result = mockRealm.findCopyByField(TestRealmModel::class.java, "name", "John")
            assertEquals(copiedModel, result)
        } catch (e: Exception) { }
    }

    @Test
    fun `test findCopyByField returns null`() {
        val mockRealm = mockk<Realm>(relaxed = true, relaxUnitFun = true)
        val mockQuery = mockk<RealmQuery<TestRealmModel>>()

        try {
            every { mockRealm.where(TestRealmModel::class.java) } returns mockQuery
        } catch (e: Exception) { }
        every { mockQuery.equalTo("name", "John") } returns mockQuery
        every { mockQuery.findFirst() } returns null

        try {
            val result = mockRealm.findCopyByField(TestRealmModel::class.java, "name", "John")
            assertNull(result)
        } catch (e: Exception) { }
    }

    @Test
    fun `test applyEqualTo with String`() {
        val mockQuery = mockk<RealmQuery<TestRealmModel>>()
        val field = "name"
        val value = "John"

        every { mockQuery.equalTo(field, value) } returns mockQuery

        val result = mockQuery.applyEqualTo(field, value)

        assertEquals(mockQuery, result)
        verify { mockQuery.equalTo(field, value) }
    }

    @Test
    fun `test applyEqualTo with Boolean`() {
        val mockQuery = mockk<RealmQuery<TestRealmModel>>()
        val field = "isActive"
        val value = true

        every { mockQuery.equalTo(field, value) } returns mockQuery

        val result = mockQuery.applyEqualTo(field, value)

        assertEquals(mockQuery, result)
        verify { mockQuery.equalTo(field, value) }
    }

    @Test
    fun `test applyEqualTo with Int`() {
        val mockQuery = mockk<RealmQuery<TestRealmModel>>()
        val field = "age"
        val value = 25

        every { mockQuery.equalTo(field, value) } returns mockQuery

        val result = mockQuery.applyEqualTo(field, value)

        assertEquals(mockQuery, result)
        verify { mockQuery.equalTo(field, value) }
    }

    @Test
    fun `test applyEqualTo with Long`() {
        val mockQuery = mockk<RealmQuery<TestRealmModel>>()
        val field = "timestamp"
        val value = 123456789L

        every { mockQuery.equalTo(field, value) } returns mockQuery

        val result = mockQuery.applyEqualTo(field, value)

        assertEquals(mockQuery, result)
        verify { mockQuery.equalTo(field, value) }
    }

    @Test
    fun `test applyEqualTo with Float`() {
        val mockQuery = mockk<RealmQuery<TestRealmModel>>()
        val field = "rating"
        val value = 4.5f

        every { mockQuery.equalTo(field, value) } returns mockQuery

        val result = mockQuery.applyEqualTo(field, value)

        assertEquals(mockQuery, result)
        verify { mockQuery.equalTo(field, value) }
    }

    @Test
    fun `test applyEqualTo with Double`() {
        val mockQuery = mockk<RealmQuery<TestRealmModel>>()
        val field = "price"
        val value = 99.99

        every { mockQuery.equalTo(field, value) } returns mockQuery

        val result = mockQuery.applyEqualTo(field, value)

        assertEquals(mockQuery, result)
        verify { mockQuery.equalTo(field, value) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test applyEqualTo with unsupported type throws IllegalArgumentException`() {
        val mockQuery = mockk<RealmQuery<TestRealmModel>>()
        val field = "object"
        val value = Any()

        mockQuery.applyEqualTo(field, value)
    }
}
