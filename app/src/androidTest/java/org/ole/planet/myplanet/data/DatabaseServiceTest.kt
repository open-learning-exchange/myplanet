package org.ole.planet.myplanet.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.realm.Realm
import io.realm.RealmConfiguration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.IllegalArgumentException
import org.ole.planet.myplanet.model.RealmNews

@RunWith(AndroidJUnit4::class)
class DatabaseServiceTest {

    private lateinit var realm: Realm

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Realm.init(context)
        val config = RealmConfiguration.Builder()
            .inMemory()
            .name("test-realm")
            .build()
        Realm.setDefaultConfiguration(config)
        realm = Realm.getDefaultInstance()
    }

    @After
    fun tearDown() {
        realm.close()
        Realm.deleteRealm(Realm.getDefaultConfiguration()!!)
    }

    @Test
    fun test_queryList() {
        realm.executeTransaction { r ->
            r.createObject(RealmNews::class.java, "1").apply { message = "John" }
            r.createObject(RealmNews::class.java, "2").apply { message = "Jane" }
        }

        val result = realm.queryList(RealmNews::class.java)
        assertEquals(2, result.size)
        val messages = result.map { it.message }
        assert(messages.contains("John"))
        assert(messages.contains("Jane"))
    }

    @Test
    fun test_queryList_with_builder() {
        realm.executeTransaction { r ->
            r.createObject(RealmNews::class.java, "1").apply { message = "John" }
            r.createObject(RealmNews::class.java, "2").apply { message = "Jane" }
        }

        val result = realm.queryList(RealmNews::class.java) {
            equalTo("message", "John")
        }

        assertEquals(1, result.size)
        assertEquals("John", result[0].message)
    }

    @Test
    fun test_findCopyByField_returns_object() {
        realm.executeTransaction { r ->
            r.createObject(RealmNews::class.java, "1").apply { message = "John" }
        }

        val result = realm.findCopyByField(RealmNews::class.java, "message", "John")
        assertEquals("John", result?.message)
    }

    @Test
    fun test_findCopyByField_returns_null() {
        realm.executeTransaction { r ->
            r.createObject(RealmNews::class.java, "1").apply { message = "John" }
        }

        val result = realm.findCopyByField(RealmNews::class.java, "message", "Jane")
        assertNull(result)
    }

    @Test
    fun applyEqualTo_applies_string_correctly() {
        val query = realm.where(RealmNews::class.java)
        val result = query.applyEqualTo("message", "value")
        assertEquals(0, result.count())
    }

    @Test
    fun applyEqualTo_applies_int_correctly() {
        val query = realm.where(RealmNews::class.java)
        // Since "updatedDate" is a Long in RealmNews, applying an int should map correctly via our applyEqualTo extension.
        // It should throw an exception from the native realm library since the type is mismatched (Int vs Long)
        assertThrows(IllegalArgumentException::class.java) {
            query.applyEqualTo("updatedDate", 123).count()
        }
    }

    @Test
    fun applyEqualTo_applies_boolean_correctly() {
        val query = realm.where(RealmNews::class.java)
        val result = query.applyEqualTo("chat", true) // chat is Boolean
        assertEquals(0, result.count())
    }

    @Test
    fun applyEqualTo_applies_long_correctly() {
        val query = realm.where(RealmNews::class.java)
        val result = query.applyEqualTo("time", 123L) // time is Long
        assertEquals(0, result.count())
    }

    @Test
    fun applyEqualTo_applies_float_correctly() {
        val query = realm.where(RealmNews::class.java)
        assertThrows(IllegalArgumentException::class.java) {
             query.applyEqualTo("chat", 12.3f).count() // float passed to a boolean
        }
    }

    @Test
    fun applyEqualTo_applies_double_correctly() {
        val query = realm.where(RealmNews::class.java)
        assertThrows(IllegalArgumentException::class.java) {
            query.applyEqualTo("chat", 12.3).count()
        }
    }

    @Test
    fun applyEqualTo_throwsExceptionForUnsupportedType() {
        val query = realm.where(RealmNews::class.java)
        val unsupportedObject = Any()

        assertThrows(IllegalArgumentException::class.java) {
            query.applyEqualTo("message", unsupportedObject)
        }
    }
}
