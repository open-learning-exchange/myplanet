package org.ole.planet.myplanet.ui.community

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.UserEntity
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommunityLeadersAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: CommunityLeadersAdapter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        adapter = CommunityLeadersAdapter(context)
    }

    @Test
    fun testOnBindViewHolder_fullNamesAndFallbacks() {
        val userWithFirstNameOnly = UserEntity().apply {
            id = "1"
            firstName = "John"
            name = "john_username"
        }
        val userWithFirstAndLastName = UserEntity().apply {
            id = "2"
            firstName = "Jane"
            lastName = "Doe"
            name = "jane_username"
        }
        val userWithMiddleName = UserEntity().apply {
            id = "3"
            firstName = "Alice"
            middleName = "M."
            lastName = "Smith"
            name = "alice_username"
        }
        val userWithNoFirstName = UserEntity().apply {
            id = "4"
            name = "fallback_username"
        }
        val userWithBlankNameFields = UserEntity().apply {
            id = "5"
            firstName = "  "
            lastName = ""
            name = "fallback_for_blank"
        }

        val parent = android.widget.FrameLayout(context)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        adapter.submitList(listOf(userWithFirstNameOnly, userWithFirstAndLastName, userWithMiddleName, userWithNoFirstName, userWithBlankNameFields))

        adapter.onBindViewHolder(viewHolder, 0)
        assertEquals("John", viewHolder.title.text.toString())

        adapter.onBindViewHolder(viewHolder, 1)
        assertEquals("Jane Doe", viewHolder.title.text.toString())

        adapter.onBindViewHolder(viewHolder, 2)
        assertEquals("Alice M. Smith", viewHolder.title.text.toString())

        adapter.onBindViewHolder(viewHolder, 3)
        assertEquals("fallback_username", viewHolder.title.text.toString())

        adapter.onBindViewHolder(viewHolder, 4)
        assertEquals("fallback_for_blank", viewHolder.title.text.toString())
    }

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
