package org.ole.planet.myplanet.services.sync

import android.content.Context
import androidx.core.content.edit
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.ole.planet.myplanet.utils.Constants.PREFS_NAME

class SyncCheckpointResetCallback(private val context: Context) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        clearCheckpoints()
    }

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        clearCheckpoints()
    }

    private fun clearCheckpoints() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keys = prefs.all.keys.filter { it.startsWith(CHECKPOINT_KEY_PREFIX) }
        if (keys.isEmpty()) return
        prefs.edit { keys.forEach { remove(it) } }
    }

    companion object {
        const val CHECKPOINT_KEY_PREFIX = "heavy_sync_skip_"
    }
}
