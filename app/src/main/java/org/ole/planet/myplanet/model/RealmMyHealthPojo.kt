package org.ole.planet.myplanet.model

import com.google.gson.*
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.JsonUtils
import java.io.*

class RealmMyHealthPojo : RealmObject {
    @PrimaryKey
    var _id: String? = null
    var userId: String? = null
    var isUpdated: Boolean = false
    var _rev: String? = null
    var data: String? = null
    var temperature: Float = 0f
    var pulse: Int = 0
    var bp: String? = null
    var height: Float = 0f
    var weight: Float = 0f
    var vision: String? = null
    var date: Long = 0
    var hearing: String? = null
    var conditions: String? = null
    var isSelfExamination: Boolean = false
    var planetCode: String? = null
    var isHasInfo: Boolean = false
    var profileId: String? = null
    var creatorId: String? = null
    var gender: String? = null
    var age: Int = 0

    fun getEncryptedDataAsJson(key: String, iv: String): JsonObject {
        return if (!data.isNullOrEmpty()) {
            Gson().fromJson(AndroidDecrypter.decrypt(data!!, key, iv), JsonObject::class.java)
        } else {
            JsonObject()
        }
    }

    companion object {
        val healthDataList: MutableList<Array<String>> = mutableListOf()

        suspend fun insert(realm: Realm, act: JsonObject?) {
            realm.write {
                var myHealth = this.query<RealmMyHealthPojo>(RealmMyHealthPojo::class, "id == $0", act?.get("_id")?.asString).first().find()
                if (myHealth == null) {
                    myHealth = RealmMyHealthPojo().apply {
                        _id = act?.get("_id")?.asString ?: ""
                    }
                    copyToRealm(myHealth)
                }
                myHealth.apply {
                    data = act?.get("data")?.asString
                    userId = act?.get("_id")?.asString
                    _rev = act?.get("_rev")?.asString
                    temperature = act?.get("temperature")?.asFloat ?: 0f
                    isUpdated = false
                    pulse = act?.get("pulse")?.asInt ?: 0
                    height = act?.get("height")?.asFloat ?: 0f
                    weight = act?.get("weight")?.asFloat ?: 0f
                    vision = act?.get("vision")?.asString
                    hearing = act?.get("hearing")?.asString
                    bp = act?.get("bp")?.asString
                    isSelfExamination = act?.get("selfExamination")?.asBoolean ?: false
                    isHasInfo = act?.get("hasInfo")?.asBoolean ?: false
                    date = act?.get("date")?.asLong ?: 0L
                    profileId = act?.get("profileId")?.asString
                    creatorId = act?.get("creatorId")?.asString
                    age = act?.get("age")?.asInt ?: 0
                    gender = act?.get("gender")?.asString
                    conditions = Gson().toJson(act?.get("conditions")?.asJsonObject)
                }

                val csvRow = arrayOf(
                    JsonUtils.getString("_id", act),
                    JsonUtils.getString("_rev", act),
                    JsonUtils.getString("data", act),
                    JsonUtils.getFloat("temperature", act).toString(),
                    JsonUtils.getInt("pulse", act).toString(),
                    JsonUtils.getString("bp", act),
                    JsonUtils.getFloat("height", act).toString(),
                    JsonUtils.getFloat("weight", act).toString(),
                    JsonUtils.getString("vision", act),
                    JsonUtils.getString("hearing", act),
                    JsonUtils.getLong("date", act).toString(),
                    JsonUtils.getBoolean("selfExamination", act).toString(),
                    JsonUtils.getString("planetCode", act),
                    JsonUtils.getBoolean("hasInfo", act).toString(),
                    JsonUtils.getString("profileId", act),
                    JsonUtils.getString("creatorId", act),
                    JsonUtils.getInt("age", act).toString(),
                    JsonUtils.getString("gender", act),
                    JsonUtils.getJsonObject("conditions", act).toString()
                )
                healthDataList.add(csvRow)
            }
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(
                    arrayOf(
                        "healthId", "health_rev", "data", "temperature", "pulse", "bp", "height",
                        "weight", "vision", "hearing", "date", "selfExamination", "planetCode",
                        "hasInfo", "profileId", "creator", "age", "gender", "conditions"
                    )
                )
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun healthWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/health.csv", healthDataList)
        }

        fun serialize(health: RealmMyHealthPojo): JsonObject {
            return JsonObject().apply {
                addProperty("_id", health.userId)
                addProperty("_rev", health._rev)
                addProperty("data", health.data)
                addProperty("temperature", health.temperature)
                addProperty("pulse", health.pulse)
                addProperty("bp", health.bp)
                addProperty("height", health.height)
                addProperty("weight", health.weight)
                addProperty("vision", health.vision)
                addProperty("hearing", health.hearing)
                addProperty("date", health.date)
                addProperty("selfExamination", health.isSelfExamination)
                addProperty("planetCode", health.planetCode)
                addProperty("hasInfo", health.isHasInfo)
                addProperty("profileId", health.profileId)
                addProperty("creatorId", health.creatorId)
                addProperty("gender", health.gender)
                addProperty("age", health.age)
                add("conditions", Gson().fromJson(health.conditions, JsonObject::class.java))
            }
        }
    }
}