package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.ole.planet.myplanet.utils.JsonUtils

@Entity(
    tableName = "offline_activity",
    indices = [Index("_rev"), Index("userId"), Index("type"), Index("_id"), Index("loginTime"), Index("userName")]
)
open class OfflineActivity {
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var _rev: String? = null
    var userName: String? = null
    var userId: String? = null
    var type: String? = null
    var description: String? = null
    var createdOn: String? = null
    var parentCode: String? = null
    var loginTime: Long? = null
    var logoutTime: Long? = null
    var androidId: String? = null
    fun changeRev(r: JsonObject?) {
        if (r != null) {
            _rev = JsonUtils.getString("_rev", r)
            _id = JsonUtils.getString("_id", r)
        }
    }
}
