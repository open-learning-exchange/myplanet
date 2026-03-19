package org.ole.planet.myplanet.data

import android.content.Context
import io.mockk.*
import io.realm.RealmQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import io.realm.RealmModel

class DatabaseServiceTest {

    interface TestModel : RealmModel

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
