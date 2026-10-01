package org.ole.planet.myplanet.utils

import java.math.BigInteger
import java.text.Normalizer
import java.util.Locale

object TextNormalizeUtils {
    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")

    fun checkNA(s: String?): String {
        return if (s.isNullOrEmpty()) "N/A" else s
    }

    fun toHex(arg: String?): String {
        return arg?.toByteArray()?.let { BigInteger(1, it).toString(16) } ?: ""
    }

    fun normalizeText(str: String): String {
        val lower = str.lowercase(Locale.getDefault())
        if (lower.all { it < '\u0080' }) {
            return lower
        }
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
    }
}
