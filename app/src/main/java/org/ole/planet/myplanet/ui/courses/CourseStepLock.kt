package org.ole.planet.myplanet.ui.courses

// TakeCourseFragment's pages: page 0 is the course detail and page n is step n.
// blockingStepIndex is the index of the first step whose exam must be passed and isn't (-1 when none).
object CourseStepLock {

    fun isPageLocked(page: Int, blockingStepIndex: Int): Boolean {
        return blockingStepIndex >= 0 && page > blockingStepIndex + 1
    }

    // The furthest page the learner can open: the step that must be passed, or the last step when none blocks
    fun lastOpenPage(blockingStepIndex: Int, stepCount: Int): Int {
        return if (blockingStepIndex < 0) stepCount else minOf(blockingStepIndex + 1, stepCount)
    }
}
