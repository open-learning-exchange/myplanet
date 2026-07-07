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
}
