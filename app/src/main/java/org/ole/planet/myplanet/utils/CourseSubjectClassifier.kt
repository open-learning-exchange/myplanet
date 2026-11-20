package org.ole.planet.myplanet.utils

import java.text.Normalizer

enum class CourseSubject {
    MATHEMATICS,
    LITERACY,
    HEALTH,
    SOCIAL_STUDIES,
    TECHNOLOGY,
    GENERAL
}

// Planet courses carry no subject: subjectLevel is the difficulty (Beginner … Expert), so it
// can't be classified. The title is the only signal, and course titles mix English, Spanish and
// French, so match clear keywords in all three and fall back to GENERAL instead of guessing.
object CourseSubjectClassifier {
    // Matched anywhere in the title, after lowercasing and stripping accents
    private val stems = listOf(
        CourseSubject.MATHEMATICS to listOf(
            "math", "matemat", "mathemat", "algebra", "geometr", "calculus", "calculo", "arithmet",
            "aritmet", "statistic", "estadistic", "fraction", "fraccion"
        ),
        CourseSubject.HEALTH to listOf(
            "health", "salud", "sante", "nutrition", "nutricion", "hygiene", "higiene", "first aid",
            "primeros auxilios", "disease", "enfermedad", "wellness", "bienestar", "malaria", "covid"
        ),
        CourseSubject.TECHNOLOGY to listOf(
            "computer", "computadora", "ordenador", "informatique", "technolog", "tecnolog",
            "programming", "programacion", "javascript", "coding", "software", "internet", "digital",
            "github", "prompt engineering", "artificial intelligence", "inteligencia artificial"
        ),
        CourseSubject.SOCIAL_STUDIES to listOf(
            "social", "civic", "civica", "history", "historia", "histoire", "geograph", "geografi",
            "econom", "financ", "emprend", "entrepreneur", "business", "negocio", "comercializ",
            "marketing", "government", "gobierno", "citizen", "ciudadan", "community", "comunidad",
            "rural", "urban"
        ),
        CourseSubject.LITERACY to listOf(
            "literacy", "alfabetiz", "reading", "lectura", "writing", "escritura", "grammar",
            "gramatica", "vocabular", "poetry", "poesia", "fairy tale", "cuento", "story", "stories"
        )
    )

    // Too short to match inside other words ("tic" in "statistics"), so whole words only
    private val words = mapOf(
        "ict" to CourseSubject.TECHNOLOGY,
        "tic" to CourseSubject.TECHNOLOGY,
        "tics" to CourseSubject.TECHNOLOGY,
        "js" to CourseSubject.TECHNOLOGY,
        "ai" to CourseSubject.TECHNOLOGY,
        "ia" to CourseSubject.TECHNOLOGY
    )

    fun classify(title: String?): CourseSubject {
        if (title.isNullOrBlank()) return CourseSubject.GENERAL
        val text = Normalizer.normalize(title.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        stems.firstOrNull { (_, keys) -> keys.any { it in text } }?.let { return it.first }
        return text.split(Regex("[^a-z0-9]+")).firstNotNullOfOrNull { words[it] } ?: CourseSubject.GENERAL
    }
}
