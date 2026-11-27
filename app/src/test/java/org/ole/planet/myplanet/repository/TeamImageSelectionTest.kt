package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TeamImageSelectionTest {

    private fun teamDoc(id: String) = JsonObject().apply { addProperty("_id", id) }

    private fun membershipDoc(id: String, teamId: String) = JsonObject().apply {
        addProperty("_id", id)
        addProperty("docType", "membership")
        addProperty("teamId", teamId)
    }

    @Test
    fun `only teams the user has joined are picked`() {
        val docs = listOf(teamDoc("joined"), teamDoc("other"))

        assertEquals(setOf("joined"), TeamsRepositoryImpl.joinedTeamsIn(docs, setOf("joined")))
    }

    @Test
    fun `a membership synced after its team still picks the team`() {
        val docs = listOf(membershipDoc("m1", teamId = "joined"))

        assertEquals(setOf("joined"), TeamsRepositoryImpl.joinedTeamsIn(docs, setOf("joined")))
    }

    @Test
    fun `nothing is picked for a user with no teams`() {
        assertEquals(emptySet<String>(), TeamsRepositoryImpl.joinedTeamsIn(listOf(teamDoc("any")), emptySet()))
    }

    @Test
    fun `image links in a description become server urls once each`() {
        val description = "Plan ![logo](resources/abc/logo.png) and again ![logo](resources/abc/logo.png)"

        val urls = TeamsRepositoryImpl.teamImageUrls(description, "https://planet.example")

        assertEquals(1, urls.size)
        assertEquals(true, urls.single().startsWith("https://planet.example/"))
        assertEquals(true, urls.single().endsWith("logo.png"))
    }
}
