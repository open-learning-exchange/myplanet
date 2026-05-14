package org.ole.planet.myplanet.model

data class StepItem(
    val id: String?,
    val stepTitle: String?,
    val questionCount: Int = 0,
    val isDescriptionVisible: Boolean = false
)
