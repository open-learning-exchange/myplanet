package org.ole.planet.myplanet.model

import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import org.ole.planet.myplanet.utils.NetworkUtils

/**
 * Room entity for crash/ANR/error logs, replacing the former Realm APK log model. Logs are written via
 * [org.ole.planet.myplanet.data.room.dao.ApkLogDao] and uploaded through the Room upload path
 * (`UploadConfigs.CrashLog`). A row with a null `_rev` is considered pending upload.
 */
@Entity(tableName = "apk_log")
open class ApkLog {
    @PrimaryKey
    var id: String = ""
    var userId: String? = null
    var type: String? = null
    var _rev: String? = null
    var error: String? = null
    var page: String? = null
    var parentCode: String? = null
    var version: String? = null
    var createdOn: String? = null
    var time: String? = null

    companion object {
        const val ERROR_TYPE_CRASH = "crash"

        fun serialize(log: ApkLog, context: Context): JsonObject {
            val `object` = JsonObject()
            `object`.addProperty("type", log.type)
            `object`.addProperty("error", log.error)
            `object`.addProperty("page", log.page)
            `object`.addProperty("time", log.time)
            `object`.addProperty("userId", log.userId)
            `object`.addProperty("version", log.version)
            `object`.addProperty("createdOn", log.createdOn)
            `object`.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            `object`.addProperty("deviceName", NetworkUtils.getDeviceName())
            `object`.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
            `object`.addProperty("parentCode", log.parentCode)
            return `object`
        }
    }
}
