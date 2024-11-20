package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmAnswer : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var value: String? = null
    var valueChoices: RealmList<String>? = null
    var mistakes = 0
    var isPassed = false
    var grade = 0
    var examId: String? = null
    var questionId: String? = null
    var submissionId: String? = null
    val valueChoicesArray: JsonArray
        get() {
            val array = JsonArray()
            if (valueChoices == null) {
                return array
            }
            for (choice in valueChoices ?: emptyList()) {
                array.add(Gson().fromJson(choice, JsonObject::class.java))
            }
            return array
        }

    fun setValueChoices(map: HashMap<String, String>?, isLastAnsValid: Boolean) {
        if (!isLastAnsValid) {
            valueChoices?.clear()
        }
        if (map != null) {
            for (key in map.keys) {
                val ob = JsonObject()
                ob.addProperty("id", map[key])
                ob.addProperty("text", key)
                valueChoices?.add(Gson().toJson(ob))
            }
        }
    }

    companion object {
        @JvmStatic
        fun serializeRealmAnswer(answers: RealmList<RealmAnswer>): JsonArray {
            val array = JsonArray()
            for (ans in answers) {
                array.add(createObject(ans))
            }
            return array
        }

        private fun createObject(ans: RealmAnswer): JsonObject {
            val `object` = JsonObject()
            if (!TextUtils.isEmpty(ans.value)) {
                `object`.addProperty("value", ans.value)
            } else {
                `object`.add("value", ans.valueChoicesArray)
            }
            `object`.addProperty("mistakes", ans.mistakes)
            `object`.addProperty("passed", ans.isPassed)
            return `object`
        }
    }
}