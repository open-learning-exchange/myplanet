package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import org.ole.planet.myplanet.model.AchievementData
import org.ole.planet.myplanet.model.RealmAchievement

interface AchievementsRepository {
    suspend fun initializeAchievement(achievementId: String): RealmAchievement?
    suspend fun updateAchievement(
        achievementId: String,
        header: String,
        goals: String,
        purpose: String,
        sendToNation: String,
        achievements: JsonArray,
        references: JsonArray
    )
    suspend fun getAchievementData(userId: String, planetCode: String): AchievementData
}
