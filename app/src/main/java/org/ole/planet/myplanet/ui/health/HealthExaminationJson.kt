package org.ole.planet.myplanet.ui.health

import kotlinx.serialization.json.JsonObject as KJsonObject
import org.ole.planet.myplanet.model.Examination
import org.ole.planet.myplanet.model.MyHealth
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.toGsonAsParsed

/** The texts a health examination is saved as: its conditions, and the plaintexts encrypted into `data`. */
internal object HealthExaminationJson {
    /** Stored in `conditions`: Gson().toJson's bytes. */
    fun conditions(conditions: Map<String?, Boolean>?): String = GsonUtils.gson.toJson(conditions)

    /** The plaintext encrypted into an examination's `data`. */
    fun sign(sign: Examination): String = GsonUtils.gson.toJson(sign)

    /** The plaintext encrypted into a health profile's `data`. */
    fun health(health: MyHealth?): String = GsonUtils.gson.toJson(health)

    /** The saved sign in decrypted examination data, or `null` when it cannot be read as one. */
    fun savedSign(decrypted: KJsonObject?): Examination? =
        decrypted?.toGsonAsParsed()?.let { runCatching { GsonUtils.gson.fromJson(it, Examination::class.java) }.getOrNull() }
}
