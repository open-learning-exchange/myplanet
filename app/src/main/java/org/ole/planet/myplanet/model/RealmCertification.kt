package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utils.JsonUtils

open class RealmCertification : RealmObject() {
    @PrimaryKey
    var _id: String? = null
    var _rev: String? = null
    var name: String? = null
    private var courseIds: String? = null

    fun setCourseIds(courseIds: JsonArray?) {
        this.courseIds = JsonUtils.gson.toJson(courseIds)
    }
}
