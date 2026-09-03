package org.ole.planet.myplanet.model

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UserEntityParseLeadersTest {

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

        val result = UserEntity.parseLeadersJson(validJson)

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

        val result = UserEntity.parseLeadersJson(validJson)

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

        val result = UserEntity.parseLeadersJson(invalidJson)

        assertEquals(0, result.size)
    }
}
