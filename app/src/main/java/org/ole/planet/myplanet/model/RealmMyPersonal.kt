package org.ole.planet.myplanet.model

import android.content.Context
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import java.util.Date
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.NetworkUtils

/**
 * Room replacement for the former Realm `RealmMyPersonal` model. The class name is kept because
 * the UI and upload path use it purely as a detached data holder. Persistence goes through
 * [org.ole.planet.myplanet.data.room.dao.PersonalDao].
 */
@Entity(tableName = "my_personal", indices = [Index("userId")])
open class RealmMyPersonal {
    // @JvmField (field access, no generated getters) so Room does not treat the local `id` and the
    // CouchDB `_id` as ambiguous accessors (getId vs get_id both normalise to "id").
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var _rev: String? = null
    var isUploaded = false
    var title: String? = null
    var description: String? = null
    var date: Long = 0
    var userId: String? = null
    var userName: String? = null
    var path: String? = null

    companion object {
        fun serialize(personal: RealmMyPersonal, context: Context): JsonObject {
            val `object` = JsonObject()
            `object`.addProperty("title", personal.title)
            `object`.addProperty("uploadDate", Date().time)
            `object`.addProperty("createdDate", personal.date)
            `object`.addProperty("filename", FileUtils.getFileNameFromUrl(personal.path))
            `object`.addProperty("author", personal.userName)
            `object`.addProperty("addedBy", personal.userName)
            `object`.addProperty("description", personal.description)
            `object`.addProperty("resourceType", "Activities")
            `object`.addProperty("private", true)
            val object1 = JsonObject()
            `object`.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            `object`.addProperty("deviceName", NetworkUtils.getDeviceName())
            `object`.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
            object1.addProperty("users", personal.userId)
            `object`.add("privateFor", object1)
            return `object`
        }
    }
}
