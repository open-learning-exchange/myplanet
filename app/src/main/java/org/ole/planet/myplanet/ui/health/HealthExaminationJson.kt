package org.ole.planet.myplanet.ui.health

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.ole.planet.myplanet.model.Examination
import org.ole.planet.myplanet.model.MyHealth
import org.ole.planet.myplanet.utils.KJson

/** The texts a health examination is saved as: its conditions, and the plaintexts encrypted into `data`. */
internal object HealthExaminationJson {
    /**
     * Stored in `conditions`: Gson().toJson's bytes for the map, members in the map's iteration
     * order, a `null` key written as `"null"`, `null` for no map.
     */
    fun conditions(conditions: Map<String?, Boolean>?): String = KJson.gsonToJson(
        conditions?.let { map -> JsonObject(map.entries.associate { (name, checked) -> name.toString() to JsonPrimitive(checked) }) }
    )

    /** The plaintext encrypted into an examination's `data`. */
    fun sign(sign: Examination): String = sign.toJsonText()

    /** The plaintext encrypted into a health profile's `data`; `null` for no profile, as Gson wrote it. */
    fun health(health: MyHealth?): String = health?.toJsonText() ?: KJson.gsonToJson(null)

    /** The saved sign in decrypted examination data, or `null` when it cannot be read as one. */
    fun savedSign(decrypted: JsonObject?): Examination? =
        decrypted?.let { runCatching { Examination.fromJson(it) }.getOrNull() }
}
