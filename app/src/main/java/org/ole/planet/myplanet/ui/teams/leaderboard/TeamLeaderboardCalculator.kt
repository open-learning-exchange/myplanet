package org.ole.planet.myplanet.ui.teams.leaderboard

import com.google.gson.JsonObject
import java.util.Calendar
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TeamLeaderboardEntry
import org.ole.planet.myplanet.repository.JoinedMemberData

object TeamLeaderboardCalculator {
    fun build(
        members: List<JoinedMemberData>,
        courseIds: List<String>,
        progressByUser: Map<String, Map<String?, JsonObject>>,
        surveyCompletionTimestampsByUser: Map<String, List<Long>> = emptyMap(),
        surveysTotal: Int = 0,
        currentUserId: String? = null,
        periodStart: Long? = null
    ): List<TeamLeaderboardEntry> {
        val entries = members.mapNotNull { member ->
            val userId = member.user.id ?: return@mapNotNull null
            val progress = progressByUser[userId].orEmpty()
            var coursesCompleted = 0
            for (courseId in courseIds) {
                val courseProgress = progress[courseId] ?: continue
                val max = courseProgress.get("max")?.asInt ?: 0
                val current = (courseProgress.get("current")?.asInt ?: 0).coerceAtMost(max)
                val completedAt = courseProgress.get("completedAt")?.asLong ?: 0L
                val isCompleted = max > 0 && current >= max &&
                    (periodStart == null || completedAt >= periodStart)
                if (isCompleted) coursesCompleted++
            }
            val surveyTimestamps = surveyCompletionTimestampsByUser[userId].orEmpty()
            val surveysCompleted = surveyTimestamps.count { periodStart == null || it >= periodStart }
            TeamLeaderboardEntry(
                visitInfo = member,
                displayName = displayName(member.user),
                coursesCompleted = coursesCompleted,
                coursesTotal = courseIds.size,
                surveysCompleted = surveysCompleted,
                surveysTotal = surveysTotal,
                isCurrentUser = !currentUserId.isNullOrEmpty() && userId == currentUserId
            )
        }
        return entries.sortedWith(
            compareByDescending<TeamLeaderboardEntry> { it.coursesCompleted }
                .thenByDescending { it.surveysCompleted }
        )
    }

    fun startOfCurrentMonth(nowMillis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowMillis
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun displayName(member: RealmUser): String {
        val combined = "${member.firstName.orEmpty()} ${member.lastName.orEmpty()}".trim()
        return combined.ifBlank { member.name.orEmpty() }
    }
}
