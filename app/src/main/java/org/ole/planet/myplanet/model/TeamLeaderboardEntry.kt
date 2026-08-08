package org.ole.planet.myplanet.model

import org.ole.planet.myplanet.repository.JoinedMemberData

data class TeamLeaderboardEntry(
    val visitInfo: JoinedMemberData,
    val displayName: String,
    val coursesCompleted: Int,
    val coursesTotal: Int,
    val surveysCompleted: Int,
    val surveysTotal: Int,
    val isCurrentUser: Boolean
) {
    val userId: String? get() = visitInfo.user.id
    val userImage: String? get() = visitInfo.user.userImage
}
