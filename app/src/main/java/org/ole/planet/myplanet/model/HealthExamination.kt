package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.KotlinxJsonUtils
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
            val kAct = act?.toKotlinx()?.jsonObject
            val myHealth = HealthExamination()
            myHealth._id = KotlinxJsonUtils.getString("_id", kAct)
            myHealth.data = KotlinxJsonUtils.getString("data", kAct)
            myHealth.userId = KotlinxJsonUtils.getString("_id", kAct)
            myHealth._rev = KotlinxJsonUtils.getString("_rev", kAct)
            myHealth.setTemperature(KotlinxJsonUtils.getFloat("temperature", kAct))
            myHealth.isUpdated = false
            myHealth.pulse = KotlinxJsonUtils.getInt("pulse", kAct)
            myHealth.height = KotlinxJsonUtils.getFloat("height", kAct)
            myHealth.setWeight(KotlinxJsonUtils.getFloat("weight", kAct))
            myHealth.vision = KotlinxJsonUtils.getString("vision", kAct)
            myHealth.hearing = KotlinxJsonUtils.getString("hearing", kAct)
            myHealth.bp = KotlinxJsonUtils.getString("bp", kAct)
            myHealth.isSelfExamination = KotlinxJsonUtils.getBoolean("selfExamination", kAct)
            myHealth.isHasInfo = KotlinxJsonUtils.getBoolean("hasInfo", kAct)
            myHealth.date = KotlinxJsonUtils.getLong("date", kAct)
            myHealth.profileId = KotlinxJsonUtils.getString("profileId", kAct)
            myHealth.creatorId = KotlinxJsonUtils.getString("creatorId", kAct)
            myHealth.age = KotlinxJsonUtils.getInt("age", kAct)
            myHealth.gender = KotlinxJsonUtils.getString("gender", kAct)
            myHealth.planetCode = KotlinxJsonUtils.getString("planetCode", kAct)
            myHealth.conditions = KotlinxJsonUtils.getJsonObject("conditions", kAct).toString()
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
