package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.json.JsonArray
import org.ole.planet.myplanet.utils.KJson

/**
 * Room replacement for the former `Certification` model. Read-only sync data (not
 * uploaded); persistence goes through [org.ole.planet.myplanet.data.room.dao.CertificationDao].
 * `courseIds` stores the certification's course-id array as a JSON string, written byte for
 * byte as Gson did because it is matched with SQL `LIKE`.
 */
@Entity(tableName = "certification")
open class Certification {
    @PrimaryKey
    var _id: String = ""
    var _rev: String? = null
    var name: String? = null
    var courseIds: String? = null

    fun setCourseIds(courseIds: JsonArray?) {
        this.courseIds = KJson.gsonToJson(courseIds, KJson.GsonNumbers.LITERAL)
    }
}
