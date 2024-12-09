package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class RealmAnswer : RealmObject {
    @PrimaryKey
    var id: String? = null
    var value: String? = null
    var valueChoices: RealmList<String> = realmListOf()
    var mistakes: Int = 0
    var isPassed: Boolean = false
    var grade: Int = 0
    var examId: String? = null
    var questionId: String? = null
    var submissionId: String? = null

    val valueChoicesArray: JsonArray get() {
        val array = JsonArray()
        for (choice in valueChoices) {
            array.add(Gson().fromJson(choice, JsonObject::class.java))
        }
        return array
    }

    fun setValueChoices(map: HashMap<String, String>?, isLastAnsValid: Boolean) {
        if (!isLastAnsValid) {
            valueChoices.clear()
        }
        map?.forEach { (key, value) ->
            val ob = JsonObject().apply {
                addProperty("id", value)
                addProperty("text", key)
            }
            valueChoices.add(Gson().toJson(ob))
        }
    }

    companion object {
        @JvmStatic
        fun serializeRealmAnswer(answers: RealmList<RealmAnswer>): JsonArray {
            val array = JsonArray()
            answers.forEach { ans ->
                array.add(createObject(ans))
            }
            return array
        }

        private fun createObject(ans: RealmAnswer): JsonObject {
            val jsonObject = JsonObject()
            if (!TextUtils.isEmpty(ans.value)) {
                jsonObject.addProperty("value", ans.value)
            } else {
                jsonObject.add("value", ans.valueChoicesArray)
            }
            jsonObject.addProperty("mistakes", ans.mistakes)
            jsonObject.addProperty("passed", ans.isPassed)
            return jsonObject
        }
    }
}
