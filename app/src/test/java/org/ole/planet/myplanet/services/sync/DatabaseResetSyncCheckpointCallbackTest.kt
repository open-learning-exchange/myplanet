package org.ole.planet.myplanet.services.sync

import android.content.Context
import androidx.core.content.edit
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.utils.Constants.PREFS_NAME
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseResetSyncCheckpointCallbackTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val db = mockk<SupportSQLiteDatabase>(relaxed = true)
    private val callback = DatabaseResetSyncCheckpointCallback(context)

    @Before
    fun setUp() {
        prefs.edit {
            clear()
            putInt("heavy_sync_skip_login_activities", 12400)
            putInt("heavy_sync_skip_courses_progress", 34600)
            putString("serverURL", "https://planet.learning.ole.org")
        }
    }

    @Test
    fun `destructive migration drops every pull checkpoint`() {
        callback.onDestructiveMigration(db)

        assertFalse(prefs.contains("heavy_sync_skip_login_activities"))
        assertFalse(prefs.contains("heavy_sync_skip_courses_progress"))
    }

    @Test
    fun `creating the store from scratch drops every pull checkpoint`() {
        callback.onCreate(db)

        assertFalse(prefs.contains("heavy_sync_skip_login_activities"))
        assertFalse(prefs.contains("heavy_sync_skip_courses_progress"))
    }

    @Test
    fun `other preferences survive the reset`() {
        callback.onDestructiveMigration(db)

        assertEquals("https://planet.learning.ole.org", prefs.getString("serverURL", null))
    }
}
