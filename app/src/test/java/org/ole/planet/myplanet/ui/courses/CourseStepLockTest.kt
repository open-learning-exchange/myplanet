package org.ole.planet.myplanet.ui.courses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseStepLockTest {

    // Four steps; step 2 (index 1) requires a pass the learner hasn't earned
    private val blockingStepIndex = 1
    private val stepCount = 4

    @Test
    fun `nothing is locked when no step blocks`() {
        (0..stepCount).forEach { page ->
            assertFalse(CourseStepLock.isPageLocked(page, -1))
        }
        assertEquals(stepCount, CourseStepLock.lastOpenPage(-1, stepCount))
    }

    @Test
    fun `next is allowed up to the blocking step and locked from it`() {
        // Next from page p opens page p + 1
        assertFalse(CourseStepLock.isPageLocked(0 + 1, blockingStepIndex))
        assertFalse(CourseStepLock.isPageLocked(1 + 1, blockingStepIndex))
        assertTrue(CourseStepLock.isPageLocked(2 + 1, blockingStepIndex))
    }

    @Test
    fun `resume stops at the blocking step`() {
        assertEquals(2, CourseStepLock.lastOpenPage(blockingStepIndex, stepCount))
        assertEquals(2, 4.coerceAtMost(CourseStepLock.lastOpenPage(blockingStepIndex, stepCount)))
        assertEquals(1, 1.coerceAtMost(CourseStepLock.lastOpenPage(blockingStepIndex, stepCount)))
    }

    @Test
    fun `a blocking last step leaves every step open`() {
        (0..stepCount).forEach { page ->
            assertFalse(CourseStepLock.isPageLocked(page, stepCount - 1))
        }
        assertEquals(stepCount, CourseStepLock.lastOpenPage(stepCount - 1, stepCount))
    }
}
