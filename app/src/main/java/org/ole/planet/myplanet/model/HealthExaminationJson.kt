package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

// App-side Gson/decryption helpers for HealthExamination, kept out of the Room entity.

fun HealthExamination.getEncryptedDataAsJson(model: UserEntity): JsonObject {
    return if (!data.isNullOrEmpty()) GsonUtils.gson.fromJson(
        AndroidDecrypter.decrypt(data, model.key, model.iv), JsonObject::class.java
    ) else JsonObject()
}

fun HealthExamination.Companion.formatConditions(conditions: String?): String {
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

fun HealthExamination.Companion.fromJson(act: JsonObject?): HealthExamination {
    val kAct = act?.toKotlinx()?.jsonObject
    val myHealth = HealthExamination()
    myHealth._id = JsonUtils.getString("_id", kAct)
    myHealth.data = JsonUtils.getString("data", kAct)
    myHealth.userId = JsonUtils.getString("_id", kAct)
    myHealth._rev = JsonUtils.getString("_rev", kAct)
    myHealth.setTemperature(JsonUtils.getFloat("temperature", kAct))
    myHealth.isUpdated = false
    myHealth.pulse = JsonUtils.getInt("pulse", kAct)
    myHealth.height = JsonUtils.getFloat("height", kAct)
    myHealth.setWeight(JsonUtils.getFloat("weight", kAct))
    myHealth.vision = JsonUtils.getString("vision", kAct)
    myHealth.hearing = JsonUtils.getString("hearing", kAct)
    myHealth.bp = JsonUtils.getString("bp", kAct)
    myHealth.isSelfExamination = JsonUtils.getBoolean("selfExamination", kAct)
    myHealth.isHasInfo = JsonUtils.getBoolean("hasInfo", kAct)
    myHealth.date = JsonUtils.getLong("date", kAct)
    myHealth.profileId = JsonUtils.getString("profileId", kAct)
    myHealth.creatorId = JsonUtils.getString("creatorId", kAct)
    myHealth.age = JsonUtils.getInt("age", kAct)
    myHealth.gender = JsonUtils.getString("gender", kAct)
    myHealth.planetCode = JsonUtils.getString("planetCode", kAct)
    myHealth.conditions = JsonUtils.getJsonObject("conditions", kAct).toString()
    return myHealth
}

fun HealthExamination.Companion.serialize(health: HealthExamination): JsonObject {
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
