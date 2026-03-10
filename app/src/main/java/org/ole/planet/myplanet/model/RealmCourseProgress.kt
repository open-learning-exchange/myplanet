package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utils.JsonUtils

open class RealmCourseProgress : RealmObject() {
    @PrimaryKey
    var id: String? = null
    @Index
    var _id: String? = null
    var createdOn: String? = null
    var createdDate: Long = 0
    var updatedDate: Long = 0
    var _rev: String? = null
    var stepNum = 0
    var passed = false
    var userId: String? = null
    var courseId: String? = null
    var parentCode: String? = null
}
