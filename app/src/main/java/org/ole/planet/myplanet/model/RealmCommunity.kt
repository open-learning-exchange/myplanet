package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room replacement for the former Realm `RealmCommunity` model. Persistence goes through
 * [org.ole.planet.myplanet.data.room.dao.CommunityDao]; the class name is kept because the sync
 * dialog and server-config utilities use it as a plain data holder.
 */
@Entity(tableName = "community")
open class RealmCommunity {
    @PrimaryKey
    var id: String = ""
    var weight: Int = 10
    var registrationRequest: String = ""
    var localDomain: String = ""
    var name: String = ""
    var parentDomain: String = ""

    override fun toString(): String {
        return name
    }
}
