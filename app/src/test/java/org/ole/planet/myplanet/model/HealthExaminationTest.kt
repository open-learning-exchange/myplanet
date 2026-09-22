package org.ole.planet.myplanet.model

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HealthExaminationTest {

    // JsonUtils.safeGet logs through android.util.Log on its fallback path, which the
    // non-boolean/malformed cases below exercise, so Log needs stubbing even though
    // formatConditions itself is pure. Mirrors JsonUtilsTest.
    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.isLoggable(any(), any()) } returns true
        every { Log.d(any(), any()) } returns 0
        every { Log.d(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun formatConditions_nullReturnsEmpty() {
        assertEquals("", HealthExamination.formatConditions(null))
    }

    @Test
    fun formatConditions_emptyReturnsEmpty() {
        assertEquals("", HealthExamination.formatConditions(""))
    }

    @Test
    fun formatConditions_blankReturnsEmpty() {
        assertEquals("", HealthExamination.formatConditions("   "))
    }

    @Test
    fun formatConditions_onlyTrueFlagsAreJoined() {
        val conditions = """
            {
              "diabetes": true,
              "hypertension": false,
              "asthma": true
            }
        """.trimIndent()

        val result = HealthExamination.formatConditions(conditions).split(", ").toSet()

        assertEquals(setOf("diabetes", "asthma"), result)
    }

    @Test
    fun formatConditions_allFalseReturnsEmpty() {
        val conditions = """{"diabetes": false, "hypertension": false}"""

        assertEquals("", HealthExamination.formatConditions(conditions))
    }

    @Test
    fun formatConditions_allTrueReturnsAll() {
        val conditions = """{"diabetes": true, "hypertension": true}"""

        val result = HealthExamination.formatConditions(conditions).split(", ").toSet()

        assertEquals(setOf("diabetes", "hypertension"), result)
    }

    @Test
    fun formatConditions_nullValueDoesNotDiscardTrueFlags() {
        val conditions = """{"diabetes": true, "asthma": null}"""

        val result = HealthExamination.formatConditions(conditions).split(", ").toSet()

        assertEquals(setOf("diabetes"), result)
    }

    @Test
    fun formatConditions_nonBooleanValueDoesNotDiscardTrueFlags() {
        val conditions = """{"diabetes": true, "asthma": {}}"""

        val result = HealthExamination.formatConditions(conditions).split(", ").toSet()

        assertEquals(setOf("diabetes"), result)
    }

    @Test
    fun formatConditions_malformedJsonReturnsEmpty() {
        assertEquals("", HealthExamination.formatConditions("{not valid json"))
    }

    @Test
    fun formatConditions_nonObjectJsonReturnsEmpty() {
        assertEquals("", HealthExamination.formatConditions("[\"diabetes\", \"asthma\"]"))
    }

    @Test
    fun formatConditions_emptyObjectReturnsEmpty() {
        assertEquals("", HealthExamination.formatConditions("{}"))
    }

    @Test
    fun serialize_omitsUnsetNumericAndStringFields() {
        val health = HealthExamination().apply {
            userId = null
            _rev = null
            data = "encrypted-blob"
            isSelfExamination = true
            isHasInfo = false
            age = 0
        }

        val json = HealthExamination.serialize(health)

        assertEquals(false, json.has("_id"))
        assertEquals(false, json.has("_rev"))
        assertEquals("encrypted-blob", json.get("data").asString)
        assertEquals(false, json.has("temperature"))
        assertEquals(false, json.has("pulse"))
        assertEquals(false, json.has("bp"))
        assertEquals(false, json.has("height"))
        assertEquals(false, json.has("weight"))
        assertEquals(false, json.has("vision"))
        assertEquals(false, json.has("hearing"))
        assertEquals(false, json.has("date"))
        assertEquals(true, json.get("selfExamination").asBoolean)
        assertEquals(false, json.has("planetCode"))
        assertEquals(false, json.get("hasInfo").asBoolean)
        assertEquals(false, json.has("profileId"))
        assertEquals(false, json.has("creatorId"))
        assertEquals(false, json.has("gender"))
        assertEquals(0, json.get("age").asInt)
        assertEquals(false, json.has("conditions"))
    }

    @Test
    fun serialize_includesEverySetField_andCreatorIdMirrorsProfileIdBug() {
        val health = HealthExamination().apply {
            userId = "user-1"
            _rev = "1-abc"
            data = "encrypted-blob"
            setTemperature(37.5f)
            pulse = 72
            bp = "120/80"
            height = 170f
            setWeight(65f)
            vision = "20/20"
            hearing = "normal"
            date = 1_700_000_000_000L
            isSelfExamination = false
            planetCode = "planet-1"
            isHasInfo = true
            profileId = "profile-1"
            creatorId = "creator-1"
            gender = "female"
            age = 42
            conditions = """{"diabetes": true}"""
        }

        val json = HealthExamination.serialize(health)

        assertEquals("user-1", json.get("_id").asString)
        assertEquals("1-abc", json.get("_rev").asString)
        assertEquals(37.5f, json.get("temperature").asFloat)
        assertEquals(72, json.get("pulse").asInt)
        assertEquals("120/80", json.get("bp").asString)
        assertEquals(170f, json.get("height").asFloat)
        assertEquals(65f, json.get("weight").asFloat)
        assertEquals("20/20", json.get("vision").asString)
        assertEquals("normal", json.get("hearing").asString)
        assertEquals(1_700_000_000_000L, json.get("date").asLong)
        assertEquals(false, json.get("selfExamination").asBoolean)
        assertEquals("planet-1", json.get("planetCode").asString)
        assertEquals(true, json.get("hasInfo").asBoolean)
        assertEquals("profile-1", json.get("profileId").asString)
        // Pre-existing behavior, not introduced by the kotlinx migration: creatorId is
        // serialized from profileId, not creatorId. Preserved as-is.
        assertEquals("profile-1", json.get("creatorId").asString)
        assertEquals("female", json.get("gender").asString)
        assertEquals(42, json.get("age").asInt)
        assertEquals(true, json.getAsJsonObject("conditions").get("diabetes").asBoolean)
    }
}
