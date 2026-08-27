package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import org.ole.planet.myplanet.utils.JsonUtils

class MyHealth {
    var profile: MyHealthProfile? = null
    var userKey: String? = null
    var lastExamination: Long = 0

    class MyHealthProfile {
        var emergencyContactName = ""
        var emergencyContactType = ""
        var emergencyContact = ""
        var specialNeeds = ""
        var notes = ""
    }

    companion object {
        /**
         * Parses an examination's conditions JSON blob and returns the names of the
         * conditions that are flagged `true`, joined by `", "`. The conditions field
         * is a JSON object mapping a condition name to a boolean. Any parse failure
         * or null/non-object input yields an empty string.
         */
        fun formatConditions(conditions: String?): String {
            if (conditions.isNullOrEmpty()) return ""
            return try {
                val conditionsMap = JsonUtils.gson.fromJson(conditions, JsonObject::class.java)
                if (conditionsMap != null) {
                    conditionsMap.keySet()
                        .filter { conditionsMap[it].asBoolean }
                        .joinToString(", ")
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        }
    }
}
