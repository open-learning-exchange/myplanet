package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.gamification.CourseCertificate
import org.ole.planet.myplanet.model.gamification.GamificationBadge
import org.ole.planet.myplanet.model.gamification.GamificationSummary
import org.ole.planet.myplanet.model.gamification.StudyStreakInfo

interface GamificationRepository {
    fun getGamificationSummaryFlow(userId: String, userName: String): Flow<GamificationSummary>
    suspend fun getGamificationSummary(userId: String, userName: String): GamificationSummary
    suspend fun calculateStudyStreaks(userId: String, userName: String): StudyStreakInfo
    suspend fun getBadges(userId: String, userName: String): List<GamificationBadge>
    suspend fun getCertificates(userId: String, userName: String): List<CourseCertificate>
}
