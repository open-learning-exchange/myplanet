package org.ole.planet.myplanet.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.MyTeam

class VoicePostingPolicyTest {

    @Test
    fun `canPost returns false if user is guest`() {
        val policy = VoicePostingPolicy(teamId = "team1", isPublic = true)
        assertFalse(policy.canPost(isGuest = true, isMember = true))
        assertFalse(policy.canPost(isGuest = true, isMember = false))
    }

    @Test
    fun `canPost returns true if user is member and not guest`() {
        val policy = VoicePostingPolicy(teamId = "team1", isPublic = false)
        assertTrue(policy.canPost(isGuest = false, isMember = true))
    }

    @Test
    fun `canPost returns true if team is public and not guest`() {
        val policy = VoicePostingPolicy(teamId = "team1", isPublic = true)
        assertTrue(policy.canPost(isGuest = false, isMember = false))
    }

    @Test
    fun `canPost returns false if team is not public and user is not member`() {
        val policy = VoicePostingPolicy(teamId = "team1", isPublic = false)
        assertFalse(policy.canPost(isGuest = false, isMember = false))
    }

    @Test
    fun `toVoicePostingPolicy maps MyTeam correctly`() {
        val team1 = MyTeam().apply {
            _id = "team_id_1"
            teamId = "team_1"
            isPublic = true
        }
        val policy1 = team1.toVoicePostingPolicy()
        assertEquals("team_id_1", policy1.teamId)
        assertTrue(policy1.isPublic)

        val team2 = MyTeam().apply {
            _id = "" // Can't be null based on entity definition
            teamId = "team_2"
            isPublic = false
        }
        val policy2 = team2.toVoicePostingPolicy()
        assertEquals("", policy2.teamId) // since _id is not null but empty, it evaluates to ""
        assertFalse(policy2.isPublic)
    }
}
