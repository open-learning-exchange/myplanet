package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for crash/ANR/error logs, replacing the former APK log model. Logs are written via
 * [org.ole.planet.myplanet.data.room.dao.ApkLogDao] and uploaded through the Room upload path
 * (`UploadConfigs.CrashLog`). A row with a null `_rev` is considered pending upload.
 */
@Entity(tableName = "apk_log", indices = [androidx.room.Index("_rev")])
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
    }
}
