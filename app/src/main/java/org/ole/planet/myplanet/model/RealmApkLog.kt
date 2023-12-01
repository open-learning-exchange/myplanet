package org.ole.planet.myplanet.model

import android.content.Context
import com.google.gson.JsonObject
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.NetworkUtils

open class RealmApkLog : RealmObject() {
    @PrimaryKey
    var id: String? = null
    @JvmField
    var type: String? = null
    private var _rev: String? = null
    @JvmField
    var error: String? = null
    @JvmField
    var page: String? = null
    @JvmField
    var parentCode: String? = null
    @JvmField
    var version: String? = null
    @JvmField
    var createdOn: String? = null
    @JvmField
    var time: String? = null
    fun get_rev(): String? {
        return _rev
    }

    fun set_rev(_rev: String?) {
        this._rev = _rev
    }

    fun setError(e: Throwable) {
        error += "--------- Stack trace ---------\n\n"
        appendReport(e)
        error += "--------- Cause ---------\n\n"
        val cause = e.cause
        appendReport(cause)
    }

    private fun appendReport(cause: Throwable?) {
        if (cause != null) {
            error += """
                $cause
                
                
                """.trimIndent()
            val arr = cause.stackTrace
            for (i in arr.indices) {
                error += """    ${arr[i]}
"""
            }
        }
        error += "-------------------------------\n\n"
    }

    companion object {
        @JvmField
        @Ignore
        val ERROR_TYPE_EXCEPTION = "exception"

        @JvmField
        @Ignore
        val ERROR_TYPE_CRASH = "crash"

        @JvmField
        @Ignore
        val ERROR_TYPE_ANR = "AnR"
        @JvmStatic
        fun serialize(log: RealmApkLog, context: Context?): JsonObject {
            val `object` = JsonObject()
            `object`.addProperty("type", log.type)
            `object`.addProperty("error", log.error)
            `object`.addProperty("page", log.page)
            `object`.addProperty("time", log.time)
            `object`.addProperty("version", log.version)
            `object`.addProperty("createdOn", log.createdOn)
            `object`.addProperty("androidId", log.createdOn)
            `object`.addProperty("createdOn", log.createdOn)
            `object`.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            `object`.addProperty("deviceName", NetworkUtils.getDeviceName())
            `object`.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
            `object`.addProperty("parentCode", log.parentCode)
            return `object`
        }
    }
}