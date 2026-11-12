package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.Achievement
import org.ole.planet.myplanet.model.AchievementData

interface UserAchievementsRepository {
    val achievementUpdates: Flow<Unit>

    suspend fun initializeAchievement(achievementId: String): Achievement?
    suspend fun updateAchievement(
        achievementId: String,
        header: String,
        goals: String,
        purpose: String,
        sendToNation: String,
        achievements: JsonArray,
        references: JsonArray,
        createdOn: String,
        username: String,
        parentCode: String,
        resumeFileName: String = ""
    )
    suspend fun getAchievementData(userId: String, planetCode: String): AchievementData
    suspend fun getAchievementsForUpload(): List<JsonObject>
    suspend fun markAchievementUploaded(id: String, rev: String?)
}
