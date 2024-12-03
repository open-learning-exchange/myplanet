package org.ole.planet.myplanet.model

import android.content.Context
import com.google.gson.JsonObject
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.NetworkUtils

class RealmApkLog : RealmObject {
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
        error = (error ?: "") + "--------- Stack trace ---------\n\n"
        appendReport(e)
        error += "--------- Cause ---------\n\n"
        appendReport(e.cause)
    }

    private fun appendReport(cause: Throwable?) {
        cause?.let {
            error += "$it\n\n"
            for (element in it.stackTrace) {
                error += "    $element\n"
            }
        }
        error += "-------------------------------\n\n"
    }

    companion object {
        @Ignore
        const val ERROR_TYPE_CRASH = "crash"

        @JvmStatic
        fun serialize(log: RealmApkLog, context: Context): JsonObject {
            val jsonObject = JsonObject().apply {
                addProperty("type", log.type)
                addProperty("error", log.error)
                addProperty("page", log.page)
                addProperty("time", log.time)
                addProperty("userId", log.userId)
                addProperty("version", log.version)
                addProperty("createdOn", log.createdOn)
                addProperty("androidId", NetworkUtils.getUniqueIdentifier())
                addProperty("deviceName", NetworkUtils.getDeviceName())
                addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
                addProperty("parentCode", log.parentCode)
            }
            return jsonObject
        }
    }
}
