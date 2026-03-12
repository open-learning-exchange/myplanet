package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils

open class RealmRating : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var createdOn: String? = null
    var _rev: String? = null
    var time: Long = 0
    var title: String? = null
    var userId: String? = null
    @Index
    var isUpdated = false
    var rate = 0
    var _id: String? = null
    var item: String? = null
    var comment: String? = null
    var parentCode: String? = null
    var planetCode: String? = null
    var type: String? = null
    var user: String? = null
}
