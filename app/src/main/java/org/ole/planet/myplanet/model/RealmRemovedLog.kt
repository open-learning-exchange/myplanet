package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "removed_log",
    indices = [Index("userId"), Index("type"), Index("docId")]
)
open class RealmRemovedLog {
    @PrimaryKey
    @JvmField
    var id: String = ""
    var userId: String? = null
    var type: String? = null
    var docId: String? = null
}
