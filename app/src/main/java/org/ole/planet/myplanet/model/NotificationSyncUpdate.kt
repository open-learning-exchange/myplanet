package org.ole.planet.myplanet.model

import androidx.room.ColumnInfo

class NotificationSyncUpdate(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "rev") val rev: String?,
    @ColumnInfo(name = "needsSync") val needsSync: Boolean = false
)
