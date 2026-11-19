package org.ole.planet.myplanet.model

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeamVoiceLabelTest {

    @Test
    fun populateTeamFields_readsObjectAndStringLabels() {
        val doc = JsonParser.parseString(
            """
            {
              "_id": "planet@parent", "type": "services", "teamType": "sync",
              "customVoiceLabels": [
                { "name": "announcement", "color": "pink" },
                { "name": "event", "color": "lime" },
                "Legacy",
                { "name": "Event", "color": "teal" },
                { "name": "  ", "color": "green" },
                { "name": "odd", "color": "magenta" }
              ]
            }
            """.trimIndent()
        ).asJsonObject
        val team = MyTeam()

        MyTeam.populateTeamFields(doc, team)

        assertEquals(
            listOf(
                TeamVoiceLabel("announcement", "pink"),
                TeamVoiceLabel("event", "lime"),
                TeamVoiceLabel("Legacy", TeamVoiceLabel.DEFAULT_COLOR),
                TeamVoiceLabel("odd", TeamVoiceLabel.DEFAULT_COLOR),
            ),
            team.customVoiceLabels
        )
    }

    @Test
    fun populateTeamFields_missingLabels_isNull() {
        val team = MyTeam()
        MyTeam.populateTeamFields(JsonParser.parseString("""{"_id":"t"}""").asJsonObject, team)
        assertNull(team.customVoiceLabels)
    }

    @Test
    fun serialize_roundTripsLabelsSoUploadsKeepThem() {
        val team = MyTeam().apply {
            _id = "planet@parent"
            type = "services"
            customVoiceLabels = listOf(TeamVoiceLabel("announcement", "pink"))
        }

        val serialized = MyTeam.serialize(team)
        val reread = MyTeam().also { MyTeam.populateTeamFields(serialized, it) }

        assertEquals(team.customVoiceLabels, reread.customVoiceLabels)
    }

    @Test
    fun colorRes_defaultHasNoOverride() {
        assertNull(TeamVoiceLabel("x").colorRes)
        assertEquals(org.ole.planet.myplanet.R.color.voice_label_pink, TeamVoiceLabel("x", "pink").colorRes)
    }
}
