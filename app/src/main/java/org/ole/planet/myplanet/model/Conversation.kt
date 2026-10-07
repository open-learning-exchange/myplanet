package org.ole.planet.myplanet.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.ole.planet.myplanet.utils.KJson

@Serializable
open class Conversation {
    var query: String? = null
    var response: String? = null

    companion object {
        /**
         * Reads a stored conversations text as Gson's `fromJson(text, Array<Conversation>)` did:
         * Gson's lenient grammar, `null` for an empty document or `null`, unknown members
         * ignored, a number or boolean read into a field as its text (numbers as written).
         * Throws [SerializationException] where Gson threw: malformed text, a document that is
         * not an array, an entry that is neither an object nor `null`, or an object or array
         * where a field's value belongs. A `null` entry is skipped, where Gson kept it in the
         * list for the screens to fail on.
         */
        fun listFromJsonText(text: String): List<Conversation>? {
            val root = KJson.parseOrNull(text) ?: return null
            if (root is JsonNull) return null
            val entries = root as? JsonArray ?: throw SerializationException("Expected an array but was $root")
            return entries.mapNotNull { entry ->
                when (entry) {
                    is JsonNull -> null
                    is JsonObject -> Conversation().apply {
                        for ((key, value) in entry) {
                            when (key) {
                                "query" -> query = readString(value)
                                "response" -> response = readString(value)
                            }
                        }
                    }
                    else -> throw SerializationException("Expected an object but was $entry")
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
