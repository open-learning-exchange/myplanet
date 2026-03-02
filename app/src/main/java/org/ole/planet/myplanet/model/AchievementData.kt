package org.ole.planet.myplanet.model

data class AchievementData(
    val goals: String = "",
    val purpose: String = "",
    val achievementsHeader: String = "",
    val achievements: List<String> = emptyList(),
    val achievementResources: List<RealmMyLibrary> = emptyList(),
    val references: List<String> = emptyList()
)
