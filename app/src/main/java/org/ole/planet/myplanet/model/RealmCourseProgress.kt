package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey

open class RealmCourseProgress : RealmObject() {
    @PrimaryKey
    var id: String? = null
    @Index
    var _id: String? = null
    var createdOn: String? = null
    var createdDate: Long = 0
    var updatedDate: Long = 0
    var _rev: String? = null
    var stepNum = 0
    var passed = false
    var userId: String? = null
    var courseId: String? = null
    var parentCode: String? = null

    companion object {
        @JvmStatic
        fun serializeProgress(progress: RealmCourseProgress): JsonObject {
            val `object` = JsonObject()
            `object`.addProperty("userId", progress.userId)
            `object`.addProperty("parentCode", progress.parentCode)
            `object`.addProperty("courseId", progress.courseId)
            `object`.addProperty("passed", progress.passed)
            `object`.addProperty("stepNum", progress.stepNum)
            `object`.addProperty("createdOn", progress.createdOn)
            `object`.addProperty("createdDate", progress.createdDate)
            `object`.addProperty("updatedDate", progress.updatedDate)
            return `object`
        }


    }
}
