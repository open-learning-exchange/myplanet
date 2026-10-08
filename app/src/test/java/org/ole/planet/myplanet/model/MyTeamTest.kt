package org.ole.planet.myplanet.model

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
}
