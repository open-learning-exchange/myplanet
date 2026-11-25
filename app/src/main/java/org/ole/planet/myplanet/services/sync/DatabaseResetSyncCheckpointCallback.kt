package org.ole.planet.myplanet.services.sync

import android.content.Context
import androidx.core.content.edit
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.ole.planet.myplanet.utils.Constants.PREFS_NAME

// Table pull checkpoints live in SharedPreferences, which outlive the Room store. When Room
// creates the store from scratch (fresh install, or a destructive migration after a schema
// bump) a leftover checkpoint would resume a pull mid-table and the rows before it would never
// be downloaded, so drop every checkpoint along with the old data.
class DatabaseResetSyncCheckpointCallback(private val context: Context) : RoomDatabase.Callback() {

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
