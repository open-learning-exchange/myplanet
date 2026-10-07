package org.ole.planet.myplanet.model

import java.io.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.KJson

class Examination : Serializable {
    var notes: String? = null
    var diagnosis: String? = null
    var treatments: String? = null
    var medications: String? = null
    var immunizations: String? = null
    var allergies: String? = null
    var xrays: String? = null
    var tests: String? = null
    var referrals: String? = null
    var createdBy: String? = null

    /**
     * The text `Gson().toJson(this)` wrote, which is the plaintext encrypted into an
     * examination's `data`: fields in declaration order, `null`s dropped, HTML-safe escaping.
     */
    fun toJsonText(): String = KJson.gsonToJson(
        buildJsonObject {
            put("notes", notes)
            put("diagnosis", diagnosis)
            put("treatments", treatments)
            put("medications", medications)
            put("immunizations", immunizations)
            put("allergies", allergies)
            put("xrays", xrays)
            put("tests", tests)
            put("referrals", referrals)
            put("createdBy", createdBy)
        }
    )

    companion object {
        /**
         * Reads a sign as Gson's reflective `fromJson(tree, Examination::class.java)` did: unknown
         * members ignored, a number or boolean read into a field as its text (numbers as
         * written), `null` as `null`. Throws [SerializationException] where Gson threw: an
         * object or array where a field's value belongs.
         */
        fun fromJson(json: JsonObject): Examination = Examination().apply {
            for ((key, value) in json) {
                when (key) {
                    "notes" -> notes = readString(value)
                    "diagnosis" -> diagnosis = readString(value)
                    "treatments" -> treatments = readString(value)
                    "medications" -> medications = readString(value)
                    "immunizations" -> immunizations = readString(value)
                    "allergies" -> allergies = readString(value)
                    "xrays" -> xrays = readString(value)
                    "tests" -> tests = readString(value)
                    "referrals" -> referrals = readString(value)
                    "createdBy" -> createdBy = readString(value)
                }
            }
        }

        private fun readString(value: JsonElement): String? = when (value) {
            is JsonNull -> null
            is JsonPrimitive -> value.content
            else -> throw SerializationException("Expected a string but was $value")
        }
    }
}
