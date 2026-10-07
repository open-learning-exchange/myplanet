package org.ole.planet.myplanet.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.KJson

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

    /**
     * The text `Gson().toJson(this)` wrote, which is the plaintext stored encrypted in
     * `health.data`: fields in declaration order, `null`s dropped, HTML-safe escaping.
     */
    fun toJsonText(): String = KJson.gsonToJson(
        buildJsonObject {
            profile?.let { profile ->
                put(
                    "profile",
                    buildJsonObject {
                        put("emergencyContactName", profile.emergencyContactName)
                        put("emergencyContactType", profile.emergencyContactType)
                        put("emergencyContact", profile.emergencyContact)
                        put("specialNeeds", profile.specialNeeds)
                        put("notes", profile.notes)
                    }
                )
            }
            put("userKey", userKey)
            put("lastExamination", lastExamination)
        }
    )

    companion object {
        /**
         * Reads health plaintext as Gson's reflective `fromJson(text, MyHealth::class.java)` did:
         * Gson's lenient grammar, `null` for null, empty or `null` text, unknown members
         * ignored, a number or boolean read into a String field as its text (numbers as
         * written), `lastExamination` from an integral number or numeric string. Throws
         * [SerializationException] where Gson threw (a non-object document, an object or array
         * where a value belongs, a non-integral `lastExamination`). One difference: a JSON
         * `null` for a profile string keeps the field's `""`, where Gson stored `null` in the
         * non-null field.
         */
        fun fromJsonText(text: String?): MyHealth? {
            val root = KJson.parseOrNull(text) ?: return null
            if (root is JsonNull) return null
            val obj = root as? JsonObject ?: throw SerializationException("Expected a JSON object but was $root")
            return MyHealth().apply {
                for ((key, value) in obj) {
                    when (key) {
                        "profile" -> profile = readProfile(value)
                        "userKey" -> userKey = readString(value)
                        "lastExamination" -> readLong(value)?.let { lastExamination = it }
                    }
                }
            }
        }

        private fun readProfile(value: JsonElement): MyHealthProfile? = when (value) {
            is JsonNull -> null
            is JsonObject -> MyHealthProfile().apply {
                for ((key, field) in value) {
                    when (key) {
                        "emergencyContactName" -> readString(field)?.let { emergencyContactName = it }
                        "emergencyContactType" -> readString(field)?.let { emergencyContactType = it }
                        "emergencyContact" -> readString(field)?.let { emergencyContact = it }
                        "specialNeeds" -> readString(field)?.let { specialNeeds = it }
                        "notes" -> readString(field)?.let { notes = it }
                    }
                }
            }
            else -> throw SerializationException("Expected an object but was $value")
        }

        /** Gson's String adapter: a string, number or boolean as its text; `null` stays `null`. */
        private fun readString(value: JsonElement): String? = when (value) {
            is JsonNull -> null
            is JsonPrimitive -> value.content
            is JsonObject, is JsonArray -> throw SerializationException("Expected a string but was $value")
        }

        /** Gson's long adapter: `null` keeps the default; an integral number or numeric string; anything else throws. */
        private fun readLong(value: JsonElement): Long? {
            if (value is JsonNull) return null
            val primitive = value as? JsonPrimitive ?: throw SerializationException("Expected a long but was $value")
            val text = primitive.content
            if (!primitive.isString && (text == "true" || text == "false")) {
                throw SerializationException("Expected a long but was $text")
            }
            try {
                return java.lang.Long.parseLong(text)
            } catch (_: NumberFormatException) {
            }
            val asDouble = try {
                java.lang.Double.parseDouble(text)
            } catch (e: NumberFormatException) {
                throw SerializationException("Expected a long but was $text", e)
            }
            val asLong = asDouble.toLong()
            if (asLong.toDouble() != asDouble) throw SerializationException("Expected a long but was $text")
            return asLong
        }
    }
}
