fun normalizeText(str: String): String {
    val lowercased = str.lowercase(java.util.Locale.ROOT)
    val normalized = java.text.Normalizer.normalize(lowercased, java.text.Normalizer.Form.NFD)
    val sb = StringBuilder(normalized.length)
    for (i in 0 until normalized.length) {
        val c = normalized[i]
        if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) {
            sb.append(c)
        }
    }
    return sb.toString()
}

fun main() {
    println(normalizeText("Café"))
}
