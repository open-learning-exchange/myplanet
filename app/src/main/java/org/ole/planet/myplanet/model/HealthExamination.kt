package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

@Entity(tableName = "health_examinations", indices = [Index("userId")])
class HealthExamination {
    @PrimaryKey
    var _id: String = ""
    var userId: String? = null
    var isUpdated = false
    var _rev: String? = null
    var data: String? = null
    var temperature = 0f
        private set
    var pulse = 0
    var bp: String? = null
    var height = 0f
    var weight = 0f
        private set
    var vision: String? = null
    var date: Long = 0
    var hearing: String? = null
    var conditions: String? = null
    var isSelfExamination = false
    var planetCode: String? = null
    var isHasInfo = false
    var profileId: String? = null
    var creatorId: String? = null
    var gender: String? = null
    var age = 0
    fun getEncryptedDataAsJson(model: UserEntity): JsonObject {
        return if (!data.isNullOrEmpty()) JsonUtils.gson.fromJson(
            AndroidDecrypter.decrypt(data, model.key, model.iv), JsonObject::class.java
        ) else JsonObject()
    }

    fun setTemperature(temperature: Float) {
        this.temperature = temperature
    }

    fun setWeight(weight: Float) {
        this.weight = weight
    }

    companion object {
        fun formatConditions(conditions: String?): String {
            if (conditions.isNullOrBlank()) return ""
            return try {
                val conditionsMap = JsonUtils.gson.fromJson(conditions, JsonObject::class.java)
                if (conditionsMap != null) {
                    conditionsMap.keySet()
                        .filter { JsonUtils.getBoolean(it, conditionsMap) }
                        .joinToString(", ")
                } else {
                    ""
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }

        fun fromJson(act: JsonObject?): HealthExamination {
            val myHealth = HealthExamination()
            myHealth._id = JsonUtils.getString("_id", act)
            myHealth.data = JsonUtils.getString("data", act)
            myHealth.userId = JsonUtils.getString("_id", act)
            myHealth._rev = JsonUtils.getString("_rev", act)
            myHealth.setTemperature(JsonUtils.getFloat("temperature", act))
            myHealth.isUpdated = false
            myHealth.pulse = JsonUtils.getInt("pulse", act)
            myHealth.height = JsonUtils.getFloat("height", act)
            myHealth.setWeight(JsonUtils.getFloat("weight", act))
            myHealth.vision = JsonUtils.getString("vision", act)
            myHealth.hearing = JsonUtils.getString("hearing", act)
            myHealth.bp = JsonUtils.getString("bp", act)
            myHealth.isSelfExamination = JsonUtils.getBoolean("selfExamination", act)
            myHealth.isHasInfo = JsonUtils.getBoolean("hasInfo", act)
            myHealth.date = JsonUtils.getLong("date", act)
            myHealth.profileId = JsonUtils.getString("profileId", act)
            myHealth.creatorId = JsonUtils.getString("creatorId", act)
            myHealth.age = JsonUtils.getInt("age", act)
            myHealth.gender = JsonUtils.getString("gender", act)
            myHealth.planetCode = JsonUtils.getString("planetCode", act)
            myHealth.conditions = JsonUtils.gson.toJson(JsonUtils.getJsonObject("conditions", act))
            return myHealth
        }

        fun serialize(health: HealthExamination): JsonObject {
            val conditionsJson = JsonUtils.gson.fromJson(health.conditions, JsonObject::class.java)
            val `object` = buildJsonObject {
                if (!health.userId.isNullOrEmpty()) put("_id", health.userId)
                if (!health._rev.isNullOrEmpty()) put("_rev", health._rev)
                put("data", health.data)
                if (health.temperature != 0f) put("temperature", health.temperature)
                if (health.pulse != 0) put("pulse", health.pulse)
                if (!health.bp.isNullOrEmpty()) put("bp", health.bp)
                if (health.height != 0f) put("height", health.height)
                if (health.weight != 0f) put("weight", health.weight)
                if (!health.vision.isNullOrEmpty()) put("vision", health.vision)
                if (!health.hearing.isNullOrEmpty()) put("hearing", health.hearing)
                if (health.date > 0) put("date", health.date)
                put("selfExamination", health.isSelfExamination)
                if (!health.planetCode.isNullOrEmpty()) put("planetCode", health.planetCode)
                put("hasInfo", health.isHasInfo)
                if (!health.profileId.isNullOrEmpty()) put("profileId", health.profileId)
                if (!health.profileId.isNullOrEmpty()) put("creatorId", health.profileId)
                if (!health.gender.isNullOrEmpty()) put("gender", health.gender)
                put("age", health.age)
                if (conditionsJson != null && conditionsJson.keySet().isNotEmpty()) {
                    put("conditions", conditionsJson.toKotlinx())
                }
            }
            return `object`.toGson()
        }
    }
}
