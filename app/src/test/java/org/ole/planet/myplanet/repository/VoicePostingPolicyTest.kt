package org.ole.planet.myplanet.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePostingPolicyTest {

    @Test
    fun `canPost returns false for guests regardless of membership or public status`() {
        val publicPolicy = VoicePostingPolicy("teamId", isPublic = true)
        val privatePolicy = VoicePostingPolicy("teamId", isPublic = false)

        assertFalse(publicPolicy.canPost(isGuest = true, isMember = false))
        assertFalse(publicPolicy.canPost(isGuest = true, isMember = true))
        assertFalse(privatePolicy.canPost(isGuest = true, isMember = false))
        assertFalse(privatePolicy.canPost(isGuest = true, isMember = true))
    }

    @Test
    fun `canPost returns true for non-guests if they are members or the team is public`() {
        val publicPolicy = VoicePostingPolicy("teamId", isPublic = true)
        val privatePolicy = VoicePostingPolicy("teamId", isPublic = false)

        assertTrue(publicPolicy.canPost(isGuest = false, isMember = false))
        assertTrue(publicPolicy.canPost(isGuest = false, isMember = true))
        assertFalse(privatePolicy.canPost(isGuest = false, isMember = false))
        assertTrue(privatePolicy.canPost(isGuest = false, isMember = true))
    }

    @Test
    fun `toVoicePostingPolicy maps correctly`() {
        // When _id is provided
        val teamWithId = org.ole.planet.myplanet.model.MyTeam().apply {
            _id = "id_123"
            teamId = "teamId_456"
            isPublic = true
        }
        val policy1 = teamWithId.toVoicePostingPolicy()
        org.junit.Assert.assertEquals("id_123", policy1.teamId)
        assertTrue(policy1.isPublic)

        // Let's also test what happens when we create a default MyTeam.
        val defaultTeam = org.ole.planet.myplanet.model.MyTeam().apply {
            isPublic = false
        }
        val policyDefault = defaultTeam.toVoicePostingPolicy()
        org.junit.Assert.assertEquals("", policyDefault.teamId)
        assertFalse(policyDefault.isPublic)
    }
}
