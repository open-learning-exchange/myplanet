package org.ole.planet.myplanet.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.utils.toGson

class MyTeamTest {
    @Test
    fun testSerializeStripsNulls() {
        val team = MyTeam()
        // Team with null name and docType
        team.name = null
        team.docType = null
        team._id = "test_id"
        team._rev = "test_rev"

        val serialized = MyTeam.serialize(team).toGson()

        // Assert that null fields are stripped from the resulting JsonObject
        assertFalse("Null name should be stripped", serialized.has("name"))
        assertFalse("Null docType should be stripped", serialized.has("docType"))
        assertTrue("Non-null _id should be present", serialized.has("_id"))
    }

    @Test
    fun serialize_omitsNonPositiveLimit() {
        val team = MyTeam().apply { _id = "t1"; docType = "team"; limit = 0 }
        assertFalse(MyTeam.serialize(team).toGson().has("limit"))

        team.limit = 12
        assertTrue(MyTeam.serialize(team).toGson().has("limit"))
    }

    @Test
    fun serialize_includesCreatedDateOnRequests() {
        val request = MyTeam().apply { _id = "r1"; docType = "request"; createdDate = 1000L }
        val serialized = MyTeam.serialize(request).toGson()

        assertTrue(serialized.has("createdDate"))
        assertFalse(serialized.has("description"))

        request.createdDate = 0L
        assertFalse(MyTeam.serialize(request).toGson().has("createdDate"))
    }

    @Test
    fun serialize_keepsReplicationFieldsOnDeletions() {
        val membership = MyTeam().apply {
            _id = "m1"; _rev = "2-a"; isDeletePending = true; docType = "membership"
            teamId = "team1"; userId = "user1"; teamPlanetCode = "planet"; teamType = "sync"; name = "ignored"
        }
        val serialized = MyTeam.serialize(membership).toGson()

        assertTrue(serialized.get("_deleted").asBoolean)
        assertTrue(serialized.has("teamPlanetCode"))
        assertTrue(serialized.has("teamType"))
        assertTrue(serialized.has("teamId"))
        assertFalse(serialized.has("name"))
        assertFalse("Null userPlanetCode should be stripped", serialized.has("userPlanetCode"))
    }
}
