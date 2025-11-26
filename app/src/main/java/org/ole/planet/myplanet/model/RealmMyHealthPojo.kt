package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils

open class RealmMyHealthPojo : RealmObject() {
    @PrimaryKey
    var _id: String? = null
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
    fun getEncryptedDataAsJson(model: RealmUserModel): JsonObject {
        return if (!TextUtils.isEmpty(data)) GsonUtils.gson.fromJson(
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
        @JvmStatic
        fun insert(mRealm: Realm, act: JsonObject?) {
            var myHealth = mRealm.where(RealmMyHealthPojo::class.java)
                .equalTo("_id", JsonUtils.getString("_id", act)).findFirst()
            if (myHealth == null) {
                myHealth = mRealm.createObject(RealmMyHealthPojo::class.java, JsonUtils.getString("_id", act))
            }
            myHealth?.data = JsonUtils.getString("data", act)
            myHealth?.userId = JsonUtils.getString("_id", act)
            myHealth?._rev = JsonUtils.getString("_rev", act)
            myHealth?.setTemperature(JsonUtils.getFloat("temperature", act))
            myHealth?.isUpdated = false
            myHealth?.pulse = JsonUtils.getInt("pulse", act)
            myHealth?.height = JsonUtils.getFloat("height", act)
            myHealth?.setWeight(JsonUtils.getFloat("weight", act))
            myHealth?.vision = JsonUtils.getString("vision", act)
            myHealth?.hearing = JsonUtils.getString("hearing", act)
            myHealth?.bp = JsonUtils.getString("bp", act)
            myHealth?.isSelfExamination = JsonUtils.getBoolean("selfExamination", act)
            myHealth?.isHasInfo = JsonUtils.getBoolean("hasInfo", act)
            myHealth?.date = JsonUtils.getLong("date", act)
            myHealth?.profileId = JsonUtils.getString("profileId", act)
            myHealth?.creatorId = JsonUtils.getString("creatorId", act)
            myHealth?.age = JsonUtils.getInt("age", act)
            myHealth?.gender = JsonUtils.getString("gender", act)
            myHealth?.planetCode = JsonUtils.getString("planetCode", act)
            myHealth?.conditions = GsonUtils.gson.toJson(JsonUtils.getJsonObject("conditions", act))
        }

        @JvmStatic
        fun serialize(health: RealmMyHealthPojo): JsonObject {
            val `object` = JsonObject()
            if (!TextUtils.isEmpty(health.userId)) `object`.addProperty("_id", health.userId)
            if (!TextUtils.isEmpty(health._rev)) `object`.addProperty("_rev", health._rev)
            `object`.addProperty("data", health.data)
            JsonUtils.addFloat(`object`, "temperature", health.temperature)
            JsonUtils.addInteger(`object`, "pulse", health.pulse)
            JsonUtils.addString(`object`, "bp", health.bp)
            JsonUtils.addFloat(`object`, "height", health.height)
            JsonUtils.addFloat(`object`, "weight", health.weight)
            JsonUtils.addString(`object`, "vision", health.vision)
            JsonUtils.addString(`object`, "hearing", health.hearing)
            JsonUtils.addLong(`object`, "date", health.date)
            `object`.addProperty("selfExamination", health.isSelfExamination)
            JsonUtils.addString(`object`, "planetCode", health.planetCode)
            `object`.addProperty("hasInfo", health.isHasInfo)
            JsonUtils.addString(`object`, "profileId", health.profileId)
            JsonUtils.addString(`object`, "creatorId", health.profileId)
            JsonUtils.addString(`object`, "gender", health.gender)
            `object`.addProperty("age", health.age)
            JsonUtils.addJson(`object`, "conditions", GsonUtils.gson.fromJson(health.conditions, JsonObject::class.java)
            )
            return `object`
        }
    }
}
