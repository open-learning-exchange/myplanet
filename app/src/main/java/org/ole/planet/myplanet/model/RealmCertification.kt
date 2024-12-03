package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.JsonUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException

class RealmCertification : RealmObject {
    @PrimaryKey
    var _id: String? = null
    var _rev: String? = null
    var name: String? = null
    var courseIds: RealmList<String>? = null

    fun setCourseIds(courseIds: JsonArray?) {
        this.courseIds?.clear()
        courseIds?.forEach { courseId ->
            this.courseIds?.add(courseId.asString)
        }
    }

    companion object {
        private val certificationDataList: MutableList<Array<String>> = mutableListOf()

        @JvmStatic
        suspend fun insert(realm: Realm, `object`: JsonObject?) {
            val id = JsonUtils.getString("_id", `object`)
            withContext(Dispatchers.IO) {
                realm.write {
                    var certification = query<RealmCertification>("_id == $0", id).first().find()
                    if (certification == null) {
                        certification = RealmCertification().apply { _id = id }
                        copyToRealm(certification)
                    }
                    certification.apply {
                        name = JsonUtils.getString("name", `object`)
                        setCourseIds(JsonUtils.getJsonArray("courseIds", `object`))
                    }
                }
            }
            val csvRow = arrayOf(
                JsonUtils.getString("_id", `object`),
                JsonUtils.getString("name", `object`),
                JsonUtils.getJsonArray("courseIds", `object`).toString()
            )
            certificationDataList.add(csvRow)
        }

        @JvmStatic
        fun isCourseCertified(realm: Realm, courseId: String?): Boolean {
            if (courseId == null) return false
            val count = realm.query<RealmCertification>("courseIds CONTAINS $0", courseId).count().find()
            return count > 0
        }

        @JvmStatic
        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                CSVWriter(FileWriter(file)).use { writer ->
                    writer.writeNext(arrayOf("certificationId", "name", "courseIds"))
                    data.forEach { row ->
                        writer.writeNext(row)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        @JvmStatic
        fun certificationWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/certification.csv", certificationDataList)
        }
    }
}

