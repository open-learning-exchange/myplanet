package org.ole.planet.myplanet.model

import android.content.Context
import org.ole.planet.myplanet.R

sealed class GradeLevel(val serverValue: String) {
    object All : GradeLevel("")
    object PreKindergarten : GradeLevel("Pre-Kindergarten")
    object Kindergarten : GradeLevel("Kindergarten")
    class Numbered(n: Int) : GradeLevel(n.toString())
    object College : GradeLevel("College")
    object PostGraduate : GradeLevel("Post-Graduate")

    fun displayString(context: Context): String = when (this) {
        is All -> context.getString(R.string.grade_all)
        is PreKindergarten -> context.getString(R.string.grade_pre_kindergarten)
        is Kindergarten -> context.getString(R.string.grade_kindergarten)
        is Numbered -> serverValue
        is College -> context.getString(R.string.grade_college)
        is PostGraduate -> context.getString(R.string.grade_post_graduate)
    }

    companion object {
        val ALL_GRADES: List<GradeLevel> = listOf(All, PreKindergarten, Kindergarten) +
            (1..12).map { Numbered(it) } + listOf(College, PostGraduate)

        fun fromServerValue(value: String?): GradeLevel? = when {
            value.isNullOrEmpty() -> All
            value == "Pre-Kindergarten" -> PreKindergarten
            value == "Kindergarten" -> Kindergarten
            value == "College" -> College
            value == "Post-Graduate" -> PostGraduate
            value.toIntOrNull() != null -> Numbered(value.toInt())
            else -> null
        }
    }
}
