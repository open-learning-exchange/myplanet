package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepProgressCellTest {

    @Test
    fun `step with only stepId maps to StepProgressCell with null percentage and false completed`() {
        val stepsArray = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("stepId", "s1")
            })
        }
        val data = CourseProgressData("Course Title", 0, 10, stepsArray)

        val cells = data.toStepCells()

        assertEquals(1, cells.size)
        assertEquals("s1", cells[0].stepId)
        assertNull(cells[0].percentage)
        assertFalse(cells[0].completed)
    }

    @Test
    fun `step with integer 0 percentage and completed false maps correctly`() {
        val stepsArray = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("stepId", "s2")
                addProperty("percentage", 0)
                addProperty("completed", false)
            })
        }
        val data = CourseProgressData("Course Title", 0, 10, stepsArray)

        val cells = data.toStepCells()

        assertEquals(1, cells.size)
        assertEquals("s2", cells[0].stepId)
        assertEquals("0", cells[0].percentage)
        assertFalse(cells[0].completed)
    }

    @Test
    fun `step with double percentage and completed true maps exact raw string`() {
        val stepsArray = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("stepId", "s3")
                addProperty("percentage", 50.0)
                addProperty("completed", true)
            })
            add(JsonObject().apply {
                addProperty("stepId", "s4")
                addProperty("percentage", 66.66666666666667)
                addProperty("completed", true)
            })
        }
        val data = CourseProgressData("Course Title", 2, 10, stepsArray)

        val cells = data.toStepCells()

        assertEquals(2, cells.size)
        assertEquals("s3", cells[0].stepId)
        assertEquals("50.0", cells[0].percentage)
        assertTrue(cells[0].completed)

        assertEquals("s4", cells[1].stepId)
        assertEquals("66.66666666666667", cells[1].percentage)
        assertTrue(cells[1].completed)
    }

    @Test
    fun `extra keys are ignored while order and size are preserved`() {
        val stepsArray = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("stepId", "step_1")
                addProperty("status", "in_progress")
                addProperty("extraKey", "extraValue")
            })
            add(JsonObject().apply {
                addProperty("stepId", "step_2")
                addProperty("percentage", 100)
                addProperty("completed", true)
                addProperty("status", "finished")
            })
        }
        val data = CourseProgressData("Course Title", 1, 2, stepsArray)

        val cells = data.toStepCells()

        assertEquals(2, cells.size)
        assertEquals(StepProgressCell("step_1", null, false), cells[0])
        assertEquals(StepProgressCell("step_2", "100", true), cells[1])
    }
}
