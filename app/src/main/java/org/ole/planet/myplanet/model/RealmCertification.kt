package org.ole.planet.myplanet.model

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.CsvUtils

open class RealmCertification : RealmObject() {
    @PrimaryKey
    var _id: String? = null
    var _rev: String? = null
    var name: String? = null
    private var courseIds: String? = null

    fun setCourseIds(courseIds: JsonArray?) {
        this.courseIds = Gson().toJson(courseIds)
    }

    companion object {
        private val certificationDataList: MutableList<Array<String>> = mutableListOf()

        @JvmStatic
        fun insert(mRealm: Realm, `object`: JsonObject?) {
            val id = JsonUtils.getString("_id", `object`)
            var certification = mRealm.where(RealmCertification::class.java).equalTo("_id", id).findFirst()
            if (certification == null) {
                certification = mRealm.createObject(RealmCertification::class.java, id)
            }
            certification?.name = JsonUtils.getString("name", `object`)
            certification?.setCourseIds(JsonUtils.getJsonArray("courseIds", `object`))
            val csvRow = arrayOf(
                JsonUtils.getString("_id", `object`),
                JsonUtils.getString("name", `object`),
                JsonUtils.getJsonArray("courseIds", `object`).toString()
            )
            certificationDataList.add(csvRow)
        }

        @JvmStatic
        fun isCourseCertified(realm: Realm, courseId: String?): Boolean {
            // FIXME
            if (courseId == null) {
                return false
            }
            val c =
                realm.where(RealmCertification::class.java).contains("courseIds", courseId).count()
            return c > 0
        }

        @JvmStatic
        fun certificationWriteCsv() {
            CsvUtils.writeCsv(
                "${context.getExternalFilesDir(null)}/ole/certification.csv",
                arrayOf(
                    "certificationId",
                    "name",
                    "courseIds"
                ),
                certificationDataList
            )
        }
    }
}
