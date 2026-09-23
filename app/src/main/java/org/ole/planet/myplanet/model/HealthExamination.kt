package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.GsonUtils
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
        return if (!data.isNullOrEmpty()) GsonUtils.gson.fromJson(
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
                val conditionsMap = GsonUtils.gson.fromJson(conditions, JsonObject::class.java)
                if (conditionsMap != null) {
                    conditionsMap.keySet()
                        .filter { GsonUtils.getBoolean(it, conditionsMap) }
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
            myHealth._id = GsonUtils.getString("_id", act)
            myHealth.data = GsonUtils.getString("data", act)
            myHealth.userId = GsonUtils.getString("_id", act)
            myHealth._rev = GsonUtils.getString("_rev", act)
            myHealth.setTemperature(GsonUtils.getFloat("temperature", act))
            myHealth.isUpdated = false
            myHealth.pulse = GsonUtils.getInt("pulse", act)
            myHealth.height = GsonUtils.getFloat("height", act)
            myHealth.setWeight(GsonUtils.getFloat("weight", act))
            myHealth.vision = GsonUtils.getString("vision", act)
            myHealth.hearing = GsonUtils.getString("hearing", act)
            myHealth.bp = GsonUtils.getString("bp", act)
            myHealth.isSelfExamination = GsonUtils.getBoolean("selfExamination", act)
            myHealth.isHasInfo = GsonUtils.getBoolean("hasInfo", act)
            myHealth.date = GsonUtils.getLong("date", act)
            myHealth.profileId = GsonUtils.getString("profileId", act)
            myHealth.creatorId = GsonUtils.getString("creatorId", act)
            myHealth.age = GsonUtils.getInt("age", act)
            myHealth.gender = GsonUtils.getString("gender", act)
            myHealth.planetCode = GsonUtils.getString("planetCode", act)
            myHealth.conditions = GsonUtils.gson.toJson(GsonUtils.getJsonObject("conditions", act))
            return myHealth
        }

        fun serialize(health: HealthExamination): JsonObject {
            val conditionsJson = GsonUtils.gson.fromJson(health.conditions, JsonObject::class.java)
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
