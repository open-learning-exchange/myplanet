package org.ole.planet.myplanet.ui.teams.leaderboard

import com.google.gson.JsonObject
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.JoinedMemberData

class TeamLeaderboardCalculatorTest {

    private fun progress(max: Int, current: Int, completedAt: Long = 0L): JsonObject {
        val obj = JsonObject()
        obj.addProperty("max", max)
        obj.addProperty("current", current)
        obj.addProperty("completedAt", completedAt)
        return obj
    }

    private fun member(id: String, name: String): JoinedMemberData {
        val user = RealmUser().apply {
            this.id = id
            this.name = name
        }
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
    fun `ranks member with more completed courses first`() {
        val alice = member("alice", "Alice")
        val bob = member("bob", "Bob")
        val courseIds = listOf("course1", "course2")
        val progressByUser = mapOf(
            "alice" to mapOf<String?, JsonObject>(
                "course1" to progress(max = 5, current = 5),
                "course2" to progress(max = 5, current = 5)
            ),
            "bob" to mapOf<String?, JsonObject>(
                "course1" to progress(max = 5, current = 5),
                "course2" to progress(max = 5, current = 1)
            )
        )

        val result = TeamLeaderboardCalculator.build(listOf(bob, alice), courseIds, progressByUser)

        assertEquals(listOf("alice", "bob"), result.map { it.userId })
        assertEquals(2, result[0].coursesCompleted)
        assertEquals(1, result[1].coursesCompleted)
    }

    @Test
    fun `breaks ties on completed courses using surveys completed`() {
        val alice = member("alice", "Alice")
        val bob = member("bob", "Bob")
        val courseIds = listOf("course1")
        val progressByUser = mapOf(
            "alice" to mapOf<String?, JsonObject>("course1" to progress(max = 5, current = 5)),
            "bob" to mapOf<String?, JsonObject>("course1" to progress(max = 5, current = 5))
        )
        val surveyTimestampsByUser = mapOf(
            "alice" to listOf(1L, 2L, 3L),
            "bob" to listOf(1L)
        )

        val result = TeamLeaderboardCalculator.build(
            listOf(bob, alice),
            courseIds,
            progressByUser,
            surveyTimestampsByUser,
            surveysTotal = 3
        )

        assertEquals(listOf("alice", "bob"), result.map { it.userId })
        assertEquals(3, result[0].surveysCompleted)
    }

    @Test
    fun `This month period excludes courses completed before the period start`() {
        val alice = member("alice", "Alice")
        val courseIds = listOf("course1", "course2")
        val progressByUser = mapOf(
            "alice" to mapOf<String?, JsonObject>(
                "course1" to progress(max = 5, current = 5, completedAt = 2_000L),
                "course2" to progress(max = 5, current = 5, completedAt = 500L)
            )
        )

        val allTime = TeamLeaderboardCalculator.build(listOf(alice), courseIds, progressByUser, periodStart = null)
        val thisMonth = TeamLeaderboardCalculator.build(listOf(alice), courseIds, progressByUser, periodStart = 1_000L)

        assertEquals(2, allTime[0].coursesCompleted)
        assertEquals(1, thisMonth[0].coursesCompleted)
    }

    @Test
    fun `This month period excludes surveys completed before the period start`() {
        val alice = member("alice", "Alice")
        val surveyTimestampsByUser = mapOf("alice" to listOf(500L, 1_500L, 2_500L))

        val allTime = TeamLeaderboardCalculator.build(
            listOf(alice), emptyList(), emptyMap(), surveyTimestampsByUser, surveysTotal = 3, periodStart = null
        )
        val thisMonth = TeamLeaderboardCalculator.build(
            listOf(alice), emptyList(), emptyMap(), surveyTimestampsByUser, surveysTotal = 3, periodStart = 1_000L
        )

        assertEquals(3, allTime[0].surveysCompleted)
        assertEquals(2, thisMonth[0].surveysCompleted)
    }

    @Test
    fun `marks the entry matching currentUserId as the current user`() {
        val alice = member("alice", "Alice")
        val bob = member("bob", "Bob")

        val result = TeamLeaderboardCalculator.build(
            listOf(alice, bob), emptyList(), emptyMap(), currentUserId = "bob"
        )

        assertEquals(false, result.first { it.userId == "alice" }.isCurrentUser)
        assertEquals(true, result.first { it.userId == "bob" }.isCurrentUser)
    }

    @Test
    fun `member with no progress data still appears with zeroed totals`() {
        val alice = member("alice", "Alice")
        val courseIds = listOf("course1")

        val result = TeamLeaderboardCalculator.build(listOf(alice), courseIds, emptyMap())

        assertEquals(1, result.size)
        assertEquals(0, result[0].coursesCompleted)
        assertEquals(1, result[0].coursesTotal)
    }

    @Test
    fun `falls back to name when first and last name are blank`() {
        val guest = member("guest1", "guestname")

        val result = TeamLeaderboardCalculator.build(listOf(guest), emptyList(), emptyMap())

        assertEquals("guestname", result[0].displayName)
    }

    @Test
    fun `startOfCurrentMonth returns the first instant of the month containing now`() {
        val now = System.currentTimeMillis()

        val start = TeamLeaderboardCalculator.startOfCurrentMonth(now)

        assertTrue(start <= now)
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = start
        assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
        assertEquals(0, calendar.get(Calendar.SECOND))
    }
}
