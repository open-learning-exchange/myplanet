package org.ole.planet.myplanet.model

import org.ole.planet.myplanet.utilities.JsonUtils
import android.text.TextUtils
import android.widget.EditText
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmAchievement : RealmObject() {
    var achievements: RealmList<String>? = null
    var references: RealmList<String>? = null
    var purpose: String? = null
    var achievementsHeader: String? = null
    var sendToNation: String? = null
    var _rev: String? = null
    @PrimaryKey
    var _id: String? = null
    var goals: String? = null

    val achievementsArray: JsonArray
        get() {
            val array = JsonArray()
            for (s in achievements ?: emptyList()) {
                val ob = JsonUtils.gson.fromJson(s, JsonElement::class.java)
                array.add(ob)
            }
            return array
        }

    fun getReferencesArray(): JsonArray {
        val array = JsonArray()
        for (s in references ?: emptyList()) {
            val ob = JsonUtils.gson.fromJson(s, JsonElement::class.java)
            array.add(ob)
        }
        return array
    }

    fun setAchievements(ac: JsonArray) {
        achievements = RealmList()
        for (el in ac) {
            val achievement = JsonUtils.gson.toJson(el)
            if (achievements?.contains(achievement) != true) {
                achievements?.add(achievement)
            }
        }
    }

    fun setReferences(of: JsonArray?) {
        references = RealmList()
        if (of == null) return
        for (el in of) {
            val e = JsonUtils.gson.toJson(el)
            if (references?.contains(e) != true) {
                references?.add(e)
            }
        }
    }

    companion object {
        @JvmStatic
        fun serialize(sub: RealmAchievement): JsonObject {
            val `object` = JsonObject()
            `object`.addProperty("_id", sub._id)
            if (!TextUtils.isEmpty(sub._rev)) `object`.addProperty("_rev", sub._rev)
            `object`.addProperty("goals", sub.goals)
            `object`.addProperty("purpose", sub.purpose)
            `object`.addProperty("achievementsHeader", sub.achievementsHeader)
            `object`.add("references", sub.getReferencesArray())
            `object`.add("achievements", sub.achievementsArray)
            return `object`
        }

        @JvmStatic
        fun createReference(name: String?, relation: EditText, phone: EditText, email: EditText): JsonObject {
            val ob = JsonObject()
            ob.addProperty("name", name)
            ob.addProperty("phone", phone.text.toString())
            ob.addProperty("relationship", relation.text.toString())
            ob.addProperty("email", email.text.toString())
            return ob
        }

        @JvmStatic
        fun insert(mRealm: Realm, act: JsonObject?) {
            var achievement = mRealm.where(RealmAchievement::class.java)
                .equalTo("_id", JsonUtils.getString("_id", act)).findFirst()
            if (achievement == null) {
                achievement = mRealm.createObject(RealmAchievement::class.java, JsonUtils.getString("_id", act))
            }
            achievement?._rev = JsonUtils.getString("_rev", act)
            achievement?.purpose = JsonUtils.getString("purpose", act)
            achievement?.goals = JsonUtils.getString("goals", act)
            achievement?.achievementsHeader = JsonUtils.getString("achievementsHeader", act)
            achievement?.setReferences(JsonUtils.getJsonArray("references", act))
            achievement?.setAchievements(JsonUtils.getJsonArray("achievements", act))
        }
    }
}
