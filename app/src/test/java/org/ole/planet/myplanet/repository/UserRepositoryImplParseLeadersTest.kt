package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserRepositoryImplParseLeadersTest {

    private lateinit var repository: UserRepositoryImpl
    private lateinit var settings: SharedPreferences

    @Before
    fun setup() {
        settings = mockk(relaxed = true)

        repository = UserRepositoryImpl(
            settings, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)
        )
    }

    @Test
    fun `parseLeadersJson parses valid json with all fields`() {
        val validJson = """
            {
              "docs": [
                {
                  "_id": "user123",
                  "name": "john_doe",
                  "firstName": "John",
                  "lastName": "Doe",
                  "email": "john@example.com"
                }
              ]
            }
        """.trimIndent()

        val result = repository.parseLeadersJson(validJson)

        assertEquals(1, result.size)
        val user = result[0]
        assertEquals("user123", user.id)
        assertEquals("john_doe", user.name)
        assertEquals("John", user.firstName)
        assertEquals("Doe", user.lastName)
        assertEquals("john@example.com", user.email)
        assertEquals(0, user.rolesList?.size ?: 0)
    }

    @Test
    fun `parseLeadersJson parses valid json with missing optional fields`() {
        val validJson = """
            {
              "docs": [
                {
                  "name": "jane_doe"
                }
              ]
            }
        """.trimIndent()

        val result = repository.parseLeadersJson(validJson)

        assertEquals(1, result.size)
        val user = result[0]
        assertEquals("org.couchdb.user:jane_doe", user.id)
        assertEquals("jane_doe", user.name)
        assertEquals(null, user.firstName)
        assertEquals(null, user.lastName)
        assertEquals(null, user.email)
        assertEquals(0, user.rolesList?.size ?: 0)
    }

    @Test
    fun `parseLeadersJson handles invalid json gracefully`() {
        val invalidJson = "{ invalid json }"

        val result = repository.parseLeadersJson(invalidJson)

        assertEquals(0, result.size)
    }
}
