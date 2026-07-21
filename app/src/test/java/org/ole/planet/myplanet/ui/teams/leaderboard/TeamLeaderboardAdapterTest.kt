package org.ole.planet.myplanet.ui.teams.leaderboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TeamLeaderboardEntry
import org.ole.planet.myplanet.repository.JoinedMemberData
import org.ole.planet.myplanet.utils.DiffUtils

class TeamLeaderboardAdapterTest {

    private fun visitInfo(userId: String): JoinedMemberData {
        val user = RealmUser().apply { id = userId }
        return JoinedMemberData(
            user = user,
            visitCount = 0,
            lastVisitDate = null,
            offlineVisits = "",
            profileLastVisit = "",
            isLeader = false
        )
    }

    @Test
    fun testAreItemsAndContentsTheSame() {
        val entry = TeamLeaderboardEntry(
            visitInfo = visitInfo("1"),
            displayName = "Alice",
            coursesCompleted = 2,
            coursesTotal = 3,
            surveysCompleted = 1,
            surveysTotal = 2,
            isCurrentUser = false
        )
        val sameEntryDifferentProgress = entry.copy(coursesCompleted = 3)
        val differentUser = entry.copy(visitInfo = visitInfo("2"))

        val callback = DiffUtils.itemCallback<TeamLeaderboardEntry>(
            areItemsTheSame = { old, new -> old.userId == new.userId },
            areContentsTheSame = { old, new -> old == new }
        )

        assertTrue(callback.areItemsTheSame(entry, sameEntryDifferentProgress))
        assertFalse(callback.areContentsTheSame(entry, sameEntryDifferentProgress))
        assertFalse(callback.areItemsTheSame(entry, differentUser))
    }
}
