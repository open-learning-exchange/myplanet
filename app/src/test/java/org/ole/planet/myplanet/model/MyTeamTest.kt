package org.ole.planet.myplanet.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyTeamTest {
    @Test
    fun testSerializeStripsNulls() {
        val team = MyTeam()
        // Team with null name and docType
        team.name = null
        team.docType = null
        team._id = "test_id"
        team._rev = "test_rev"

        val serialized = MyTeam.serialize(team)

        // Assert that null fields are stripped from the resulting JsonObject
        assertFalse("Null name should be stripped", serialized.has("name"))
        assertFalse("Null docType should be stripped", serialized.has("docType"))
        assertTrue("Non-null _id should be present", serialized.has("_id"))
    }

    @Test
    fun testSerializeOmitsUnsetTeamLimit() {
        val team = MyTeam().apply {
            _id = "team_id"
            name = "Team"
        }

        val serialized = MyTeam.serialize(team)

        assertFalse("A limit of 0 would block Planet from accepting join requests", serialized.has("limit"))
    }

    @Test
    fun testSerializeKeepsTeamLimit() {
        val team = MyTeam().apply {
            _id = "team_id"
            name = "Team"
            limit = 12
        }

        val serialized = MyTeam.serialize(team)

        assertEquals(12, serialized.get("limit").asInt)
    }

    @Test
    fun testSerializeRequestKeepsCreatedDate() {
        val team = MyTeam().apply {
            _id = "request_id"
            docType = "request"
            teamId = "team_id"
            userId = "org.couchdb.user:tester"
            createdDate = 1_700_000_000_000L
        }

        val serialized = MyTeam.serialize(team)

        assertEquals(1_700_000_000_000L, serialized.get("createdDate").asLong)
        assertFalse("Requests carry no member limit", serialized.has("limit"))
    }
}
