package org.ole.planet.myplanet.ui.teams.members

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.RealmUser

class RequestsAdapterTest {

    @Test
    fun testAreContentsTheSame() {
        val oldUser = RealmUser().apply {
            id = "1"
            name = "John Doe"
            email = "john@example.com"
        }

        val newUserSame = RealmUser().apply {
            id = "1"
            name = "John Doe"
            email = "john@example.com"
        }

        val newUserDifferentName = RealmUser().apply {
            id = "1"
            name = "Jane Doe"
            email = "john@example.com"
        }

        val newUserDifferentEmail = RealmUser().apply {
            id = "1"
            name = "John Doe"
            email = "jane@example.com"
        }

        val newUserDifferentId = RealmUser().apply {
            id = "2"
            name = "John Doe"
            email = "john@example.com"
        }

        val callback = RequestsAdapter.MWC_DIFF_CALLBACK

        assertTrue(callback.areContentsTheSame(oldUser, newUserSame))
        assertFalse(callback.areContentsTheSame(oldUser, newUserDifferentName))
        assertFalse(callback.areContentsTheSame(oldUser, newUserDifferentEmail))
        assertFalse(callback.areContentsTheSame(oldUser, newUserDifferentId))
    }
}
