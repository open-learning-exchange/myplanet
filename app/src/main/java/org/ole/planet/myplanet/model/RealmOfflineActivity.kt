package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utils.JsonUtils

open class RealmOfflineActivity : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    @Index
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
