package org.ole.planet.myplanet.ui.community

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.UserEntity

class CommunityLeadersAdapterTest {

    @Test
    fun testAreContentsTheSame() {
        val oldUser = UserEntity().apply {
            id = "1"
            firstName = "John"
            lastName = "Doe"
            email = "john@example.com"
            name = "John Doe"
        }

        val newUserSame = UserEntity().apply {
            id = "1"
            firstName = "John"
            lastName = "Doe"
            email = "john@example.com"
            name = "John Doe"
        }

        val newUserDifferentFirstName = UserEntity().apply {
            id = "1"
            firstName = "Jane"
            lastName = "Doe"
            email = "john@example.com"
            name = "John Doe"
        }

        val newUserDifferentLastName = UserEntity().apply {
            id = "1"
            firstName = "John"
            lastName = "Smith"
            email = "john@example.com"
            name = "John Doe"
        }

        val newUserDifferentEmail = UserEntity().apply {
            id = "1"
            firstName = "John"
            lastName = "Doe"
            email = "jane@example.com"
            name = "John Doe"
        }

        val newUserDifferentName = UserEntity().apply {
            id = "1"
            firstName = "John"
            lastName = "Doe"
            email = "john@example.com"
            name = "Jane Doe"
        }

        val callback = org.ole.planet.myplanet.utils.DiffUtils.itemCallback<UserEntity>(
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
            areContentsTheSame = { oldItem, newItem ->
                oldItem.firstName == newItem.firstName &&
                    oldItem.lastName == newItem.lastName &&
                    oldItem.email == newItem.email &&
                    oldItem.name == newItem.name
            }
        )

        assertTrue(callback.areContentsTheSame(oldUser, newUserSame))
        assertFalse(callback.areContentsTheSame(oldUser, newUserDifferentFirstName))
        assertFalse(callback.areContentsTheSame(oldUser, newUserDifferentLastName))
        assertFalse(callback.areContentsTheSame(oldUser, newUserDifferentEmail))
        assertFalse(callback.areContentsTheSame(oldUser, newUserDifferentName))
    }
}
