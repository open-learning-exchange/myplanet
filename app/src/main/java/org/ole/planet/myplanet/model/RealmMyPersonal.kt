package org.ole.planet.myplanet.model

import android.content.Context
import com.google.gson.JsonObject
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import java.util.Date

open class RealmMyPersonal : RealmObject() {
    @PrimaryKey
    var id: String? = null
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
