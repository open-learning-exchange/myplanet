package org.ole.planet.myplanet.model

import android.content.Context
import com.google.gson.JsonObject
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utils.NetworkUtils

open class RealmApkLog : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var userId: String? = null
    var type: String? = null
    var _rev: String? = null
    var error: String? = null
    var page: String? = null
    var parentCode: String? = null
    var version: String? = null
    var createdOn: String? = null
    var time: String? = null

    fun setError(e: Throwable) {
        val builder = StringBuilder(error ?: "")
        builder.append("--------- Stack trace ---------\n\n")
        appendReport(builder, e)
        builder.append("--------- Cause ---------\n\n")
        val cause = e.cause
        appendReport(builder, cause)
        error = builder.toString()
    }

    private fun appendReport(builder: StringBuilder, cause: Throwable?) {
        if (cause != null) {
            builder.append(cause.toString()).append("\n\n\n")
            val arr = cause.stackTrace
            for (i in arr.indices) {
                builder.append("    ").append(arr[i]).append("\n")
            }
        }
        builder.append("-------------------------------\n\n")
    }

    companion object {
        @Ignore
        const val ERROR_TYPE_CRASH = "crash"

        @JvmStatic
        fun serialize(log: RealmApkLog, context: Context): JsonObject {
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
