package org.ole.planet.myplanet.model

import androidx.annotation.ColorRes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.R

@Serializable
data class TeamVoiceLabel(
    val name: String,
    val color: String = DEFAULT_COLOR,
) {
    @get:ColorRes
    val colorRes: Int?
        get() = COLOR_RES[color]

    fun matches(label: String?): Boolean = label != null && label.equals(name, ignoreCase = true)

    companion object {
        const val DEFAULT_COLOR = "default"

        private val COLOR_RES: Map<String, Int> = mapOf(
            "green" to R.color.voice_label_green,
            "teal" to R.color.voice_label_teal,
            "purple" to R.color.voice_label_purple,
            "pink" to R.color.voice_label_pink,
            "lime" to R.color.voice_label_lime,
            "brown" to R.color.voice_label_brown,
        )

        fun parse(array: JsonArray): List<TeamVoiceLabel> {
            val seen = mutableSetOf<String>()
            return array.mapNotNull { element ->
                val (rawName, rawColor) = when (element) {
                    is JsonPrimitive -> if (element.isString) element.content to null else return@mapNotNull null
                    is JsonObject -> {
                        val name = (element["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                            ?: return@mapNotNull null
                        name to (element["color"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    }
                    else -> return@mapNotNull null
                }
                val name = rawName.trim()
                if (name.isEmpty() || !seen.add(name.lowercase())) return@mapNotNull null
                val color = rawColor?.takeIf { it in COLOR_RES } ?: DEFAULT_COLOR
                TeamVoiceLabel(name, color)
            }
        }

        fun toJsonArray(labels: List<TeamVoiceLabel>): JsonArray = buildJsonArray {
            labels.forEach { label ->
                add(buildJsonObject {
                    put("name", label.name)
                    put("color", label.color)
                })
            }
        }
    }
}
