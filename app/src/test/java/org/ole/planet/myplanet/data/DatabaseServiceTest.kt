package org.ole.planet.myplanet.data

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmModel
import io.realm.RealmQuery
import io.realm.RealmResults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.lang.IllegalArgumentException

open class TestRealmModel : RealmModel {
    var id: String = ""
    var name: String = ""
}

class DatabaseServiceTest {

    interface TestModel : RealmModel

    @Test
    fun `test queryList`() {
        val mockRealm = mockk<Realm>(relaxed = true, relaxUnitFun = true)
        val mockQuery = mockk<RealmQuery<TestRealmModel>>(relaxed = true)
        val mockResults = mockk<RealmResults<TestRealmModel>>(relaxed = true)
        val testModel1 = TestRealmModel().apply { id = "1"; name = "John" }
        val testModel2 = TestRealmModel().apply { id = "2"; name = "Jane" }
        val copiedList = listOf(testModel1, testModel2)

        every { mockRealm.where(TestRealmModel::class.java) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockRealm.copyFromRealm(mockResults) } returns copiedList

        val result = mockRealm.queryList(TestRealmModel::class.java)
        assertEquals(copiedList, result)
    }

    @Test
    fun `test queryList with builder`() {
        val mockRealm = mockk<Realm>(relaxed = true, relaxUnitFun = true)
        val mockQuery = mockk<RealmQuery<TestRealmModel>>(relaxed = true)
        val mockResults = mockk<RealmResults<TestRealmModel>>(relaxed = true)
        val testModel1 = TestRealmModel().apply { id = "1"; name = "John" }
        val copiedList = listOf(testModel1)

        every { mockRealm.where(TestRealmModel::class.java) } returns mockQuery
        every { mockQuery.equalTo("name", "John") } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockRealm.copyFromRealm(mockResults) } returns copiedList

        val result = mockRealm.queryList(TestRealmModel::class.java) {
            equalTo("name", "John")
        }
        assertEquals(copiedList, result)
    }

    @Test
    fun `test findCopyByField returns object`() {
        val mockRealm = mockk<Realm>(relaxed = true, relaxUnitFun = true)
        val mockQuery = mockk<RealmQuery<TestRealmModel>>(relaxed = true)
        val testModel = TestRealmModel().apply { id = "1"; name = "John" }
        val copiedModel = TestRealmModel().apply { id = "1"; name = "John" }

        every { mockRealm.where(TestRealmModel::class.java) } returns mockQuery
        every { mockQuery.equalTo("name", "John") } returns mockQuery
        every { mockQuery.findFirst() } returns testModel
        // Using any() allows bypassing the managed check when setting up the mock
        every { mockRealm.copyFromRealm(any<TestRealmModel>()) } returns copiedModel

        val result = mockRealm.findCopyByField(TestRealmModel::class.java, "name", "John")
        assertEquals(copiedModel, result)
    }

    @Test
    fun `test findCopyByField returns null`() {
        val mockRealm = mockk<Realm>(relaxed = true, relaxUnitFun = true)
        val mockQuery = mockk<RealmQuery<TestRealmModel>>(relaxed = true)

        every { mockRealm.where(TestRealmModel::class.java) } returns mockQuery
        every { mockQuery.equalTo("name", "John") } returns mockQuery
        every { mockQuery.findFirst() } returns null

        val result = mockRealm.findCopyByField(TestRealmModel::class.java, "name", "John")
        assertNull(result)
    }

    @Test
    fun `applyEqualTo applies string correctly`() {
        val mockQuery = mockk<RealmQuery<TestModel>>(relaxed = true)
        every { mockQuery.equalTo("field", "value") } returns mockQuery

        val result = mockQuery.applyEqualTo("field", "value")

        assertEquals(mockQuery, result)
        verify { mockQuery.equalTo("field", "value") }
    }

    @Test
    fun `applyEqualTo applies int correctly`() {
        val mockQuery = mockk<RealmQuery<TestModel>>(relaxed = true)
        every { mockQuery.equalTo("field", 123) } returns mockQuery

        val result = mockQuery.applyEqualTo("field", 123)

        assertEquals(mockQuery, result)
        verify { mockQuery.equalTo("field", 123) }
    }

    @Test
    fun `applyEqualTo applies boolean correctly`() {
        val mockQuery = mockk<RealmQuery<TestModel>>(relaxed = true)
        every { mockQuery.equalTo("field", true) } returns mockQuery

        val result = mockQuery.applyEqualTo("field", true)

        assertEquals(mockQuery, result)
        verify { mockQuery.equalTo("field", true) }
    }

    @Test
    fun `applyEqualTo applies long correctly`() {
        val mockQuery = mockk<RealmQuery<TestModel>>(relaxed = true)
        every { mockQuery.equalTo("field", 123L) } returns mockQuery

        val result = mockQuery.applyEqualTo("field", 123L)

        assertEquals(mockQuery, result)
        verify { mockQuery.equalTo("field", 123L) }
    }

    @Test
    fun `applyEqualTo applies float correctly`() {
        val mockQuery = mockk<RealmQuery<TestModel>>(relaxed = true)
        every { mockQuery.equalTo("field", 12.3f) } returns mockQuery

        val result = mockQuery.applyEqualTo("field", 12.3f)

        assertEquals(mockQuery, result)
        verify { mockQuery.equalTo("field", 12.3f) }
    }

    @Test
    fun `applyEqualTo applies double correctly`() {
        val mockQuery = mockk<RealmQuery<TestModel>>(relaxed = true)
        every { mockQuery.equalTo("field", 12.3) } returns mockQuery

        val result = mockQuery.applyEqualTo("field", 12.3)

        assertEquals(mockQuery, result)
        verify { mockQuery.equalTo("field", 12.3) }
    }

    @Test
    fun `applyEqualTo throws exception for unsupported type`() {
        val mockQuery = mockk<RealmQuery<TestModel>>(relaxed = true)
        val unsupportedObject = object {}

        assertThrows(IllegalArgumentException::class.java) {
            mockQuery.applyEqualTo("field", unsupportedObject)
        }
    }
}
