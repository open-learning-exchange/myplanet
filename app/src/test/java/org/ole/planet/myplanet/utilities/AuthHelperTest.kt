package org.ole.planet.myplanet.utilities

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.realm.Realm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmUserModel

class AuthHelperTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk()
        every { context.getString(R.string.username_cannot_be_empty) } returns "Username cannot be empty"
        every { context.getString(R.string.invalid_username) } returns "Invalid username"
        every { context.getString(R.string.must_start_with_letter_or_number) } returns "Must start with letter or number"
        every { context.getString(R.string.only_letters_numbers_and_are_allowed) } returns "Only letters numbers and _ . - are allowed"
        every { context.getString(R.string.username_taken) } returns "Username taken"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun validateUsername_allowsValidUsernames() {
        listOf("user1", "User_2", "user-3", "user.4").forEach { username ->
            assertNull(AuthHelper.validateUsername(context, username))
        }
    }

    @Test
    fun validateUsername_rejectsInvalidCharacters() {
        val result = AuthHelper.validateUsername(context, "invalid\$user")
        assertEquals("Only letters numbers and _ . - are allowed", result)
    }

    @Test
    fun validateUsername_rejectsUsernamesStartingWithNonAlphanumeric() {
        val result = AuthHelper.validateUsername(context, "_start")
        assertEquals("Must start with letter or number", result)
    }

    @Test
    fun validateUsername_rejectsDuplicateUsername() {
        mockkObject(RealmUserModel.Companion)
        every { RealmUserModel.isUserExists(any(), "existing") } returns true
        val realm = mockk<Realm>()

        val result = AuthHelper.validateUsername(context, "existing", realm)

        assertEquals("Username taken", result)
    }
}
