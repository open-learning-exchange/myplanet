package org.ole.planet.myplanet.utilities

import java.text.Normalizer
import java.util.Locale

object TextUtils {
    fun normalizeText(str: String): String {
        return Normalizer.normalize(str.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
}
