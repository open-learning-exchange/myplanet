package org.ole.planet.myplanet.ui.health

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.ole.planet.myplanet.model.Examination
import org.ole.planet.myplanet.model.MyHealth
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesRawGolden
import org.ole.planet.myplanet.utils.KJson

/** Pins the texts a health examination is saved as, and how a saved sign is read back. */
class HealthExaminationJsonGoldenTest {
    @Test
    fun `conditions keep their stored bytes`() {
        val conditions = HashMap<String?, Boolean>().apply {
            put("Fever", true)
            put("Cough", false)
            put(TRICKY, true)
            put("<script>&'=", false)
            put(null, true)
        }
        val texts = listOf(
            HealthExaminationJson.conditions(conditions),
            HealthExaminationJson.conditions(HashMap()),
            HealthExaminationJson.conditions(null),
        )
        assertMatchesRawGolden("ui/health/conditions", texts.joinToString("\n"))
    }

    @Test
    fun `sign plaintext keeps its bytes`() {
        val full = Examination().apply {
            notes = "notes $TRICKY"
            diagnosis = "diagnosis <b>"
            treatments = "treatments & more"
            medications = "medications 'x'"
            immunizations = "immunizations = y"
            allergies = "allergies"
            xrays = "xrays"
            tests = "tests"
            referrals = "referrals"
            createdBy = "org.couchdb.user:someone"
        }
        val partial = Examination().apply {
            allergies = ""
            createdBy = "creator"
        }
        val texts = listOf(
            HealthExaminationJson.sign(full),
            HealthExaminationJson.sign(partial),
            HealthExaminationJson.sign(Examination()),
        )
        assertMatchesRawGolden("ui/health/sign-plaintexts", texts.joinToString("\n"))
    }

    @Test
    fun `health plaintext keeps its bytes`() {
        val full = MyHealth().apply {
            userKey = "key <k>&"
            lastExamination = 1_700_000_000_123L
            profile = MyHealth.MyHealthProfile().apply {
                emergencyContactName = TRICKY
                emergencyContactType = "phone"
                emergencyContact = "+1 (555) 'x'"
                specialNeeds = "needs = none"
                notes = ""
            }
        }
        val texts = listOf(
            HealthExaminationJson.health(full),
            HealthExaminationJson.health(MyHealth()),
            HealthExaminationJson.health(null),
        )
        assertMatchesRawGolden("ui/health/health-plaintexts", texts.joinToString("\n"))
    }

    @Test
    fun `savedSign reads decrypted data as the examination screen does`() {
        val inputs = listOf(
            """{"notes":"n","diagnosis":"d <b>","treatments":"t","medications":"m","immunizations":"i","allergies":"a","xrays":"x","tests":"t2","referrals":"r","createdBy":"c"}""",
            """{"notes":1.50,"diagnosis":1e2,"treatments":true,"medications":null,"unknown":{"x":1}}""",
            """{"notes":{"a":1}}""",
            """{"diagnosis":["a"]}""",
            """{"notes":unquoted}""",
            "{}",
            null,
        )
        val rows = JsonArray(
            inputs.map { input ->
                buildJsonObject {
                    put("decrypted", input)
                    put("sign", render(HealthExaminationJson.savedSign(KJson.parseObjectOrNull(input))))
                }
            }
        )
        assertMatchesGolden("ui/health/saved-signs", rows)
    }

    private fun render(sign: Examination?): JsonElement = if (sign == null) JsonNull else buildJsonObject {
        put("notes", sign.notes)
        put("diagnosis", sign.diagnosis)
        put("treatments", sign.treatments)
        put("medications", sign.medications)
        put("immunizations", sign.immunizations)
        put("allergies", sign.allergies)
        put("xrays", sign.xrays)
        put("tests", sign.tests)
        put("referrals", sign.referrals)
        put("createdBy", sign.createdBy)
    }
}
