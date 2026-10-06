package org.ole.planet.myplanet.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidKeyValueStoreTest {

    private lateinit var preferences: SharedPreferences
    private lateinit var store: AndroidKeyValueStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        store = AndroidKeyValueStore(preferences)
    }

    @Test
    fun `typed values round-trip`() {
        store.putString("s", "value")
        store.putBoolean("b", true)
        store.putInt("i", 42)
        store.putLong("l", 1_700_000_000_000L)
        store.putFloat("f", 1.5f)

        assertEquals("value", store.getString("s", null))
        assertTrue(store.getBoolean("b", false))
        assertEquals(42, store.getInt("i", 0))
        assertEquals(1_700_000_000_000L, store.getLong("l", 0L))
        assertEquals(1.5f, store.getFloat("f", 0f))
        assertEquals(setOf("s", "b", "i", "l", "f"), store.keys())
    }

    @Test
    fun `absent keys return the given defaults`() {
        assertNull(store.getString("missing", null))
        assertEquals("fallback", store.getString("missing", "fallback"))
        assertTrue(store.getBoolean("missing", true))
        assertEquals(-1, store.getInt("missing", -1))
        assertEquals(7L, store.getLong("missing", 7L))
        assertEquals(1.0f, store.getFloat("missing", 1.0f))
        assertFalse(store.contains("missing"))
    }

    @Test
    fun `reads values written through plain SharedPreferences`() {
        preferences.edit()
            .putString("serverURL", "http://planet.example")
            .putBoolean("isLoggedIn", true)
            .putInt("autoSyncInterval", 3 * 60 * 60)
            .putLong("LastSync", 123L)
            .putFloat("media_playback_speed", 1.25f)
            .commit()

        val reopened = AndroidKeyValueStore(preferences)

        assertEquals("http://planet.example", reopened.getString("serverURL", ""))
        assertTrue(reopened.getBoolean("isLoggedIn", false))
        assertEquals(3 * 60 * 60, reopened.getInt("autoSyncInterval", 0))
        assertEquals(123L, reopened.getLong("LastSync", 0L))
        assertEquals(1.25f, reopened.getFloat("media_playback_speed", 1.0f))
        assertTrue(reopened.contains("serverURL"))
    }

    @Test
    fun `writes are visible to plain SharedPreferences under the same keys`() {
        store.edit {
            putString("url_user", "satellite")
            putBoolean("isAlternativeUrl", true)
            putLong("lastLogin", 99L)
        }

        assertEquals("satellite", preferences.getString("url_user", null))
        assertTrue(preferences.getBoolean("isAlternativeUrl", false))
        assertEquals(99L, preferences.getLong("lastLogin", 0L))
    }

    @Test
    fun `null string and remove delete the key`() {
        store.putString("a", "1")
        store.putString("b", "2")

        store.putString("a", null)
        store.remove("b")

        assertFalse(preferences.contains("a"))
        assertFalse(preferences.contains("b"))
    }

    @Test
    fun `clear in a batch runs before its puts`() {
        store.putString("old", "x")

        store.edit {
            putBoolean("firstLaunch", true)
            clear()
        }

        assertEquals(setOf("firstLaunch"), store.keys())
        assertTrue(store.getBoolean("firstLaunch", false))
    }
}
