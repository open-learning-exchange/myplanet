package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-table resume point for the CouchDB `_changes` feed (opaque `last_seq` token, not an
 * integer offset). Living in Room — not SharedPreferences — is load-bearing: RoomModule builds
 * the database with fallbackToDestructiveMigration, so a schema-version bump wipes this table
 * along with everything else, forcing the next sync to start fresh from since=0 instead of
 * wrongly reporting "nothing changed" against data that no longer exists locally.
 */
@Entity(tableName = "sync_cursors")
data class SyncCursor(
    @PrimaryKey val tableName: String,
    val since: String
)
