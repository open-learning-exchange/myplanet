package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room replacement for the former Realm `ChatHistory` model. The nested conversation list is
 * stored as embedded JSON (see [org.ole.planet.myplanet.data.room.Converters]); persistence goes
 * through [org.ole.planet.myplanet.data.room.dao.ChatDao].
 */
@Entity(tableName = "chat_history")
open class ChatHistory {
    // @JvmField on id/_id so Room does not see ambiguous getId/get_id accessors.
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var _rev: String? = null
    var user: String? = null
    var aiProvider: String? = null
    var title: String? = null
    var createdDate: String? = null
    var updatedDate: String? = null
    var lastUsed: Long = 0
    var conversations: List<Conversation>? = null
}
