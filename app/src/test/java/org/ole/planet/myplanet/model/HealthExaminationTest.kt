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
}
