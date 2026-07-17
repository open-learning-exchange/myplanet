package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.JsonArray
import org.ole.planet.myplanet.utils.JsonUtils

/**
 * Room replacement for the former Realm `RealmCertification` model. Read-only sync data (not
 * uploaded); persistence goes through [org.ole.planet.myplanet.data.room.dao.CertificationDao].
 * `courseIds` stores the certification's course-id array as a JSON string.
 */
@Entity(tableName = "certification")
open class RealmCertification {
    @PrimaryKey
    var _id: String = ""
    var _rev: String? = null
    var name: String? = null
    var courseIds: String? = null

    fun setCourseIds(courseIds: JsonArray?) {
        this.courseIds = JsonUtils.gson.toJson(courseIds)
    }
}
