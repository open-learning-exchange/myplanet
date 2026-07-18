package org.ole.planet.myplanet.data.room.entity.legacy

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room mirror for legacy RealmUser rows during the Realm-to-Room migration. */
@Entity(tableName = "users", indices = [Index("_id"), Index("name"), Index("planetCode")])
data class RoomUserEntity(
    @PrimaryKey @JvmField val id: String,
    @JvmField val _id: String? = null,
    @JvmField val _rev: String? = null,
    val name: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val rolesList: List<String>? = null,
    val planetCode: String? = null,
    val parentCode: String? = null,
    val isUserAdmin: Boolean = false,
    val joined: Long = 0,
    val updated: Long = 0,
)
