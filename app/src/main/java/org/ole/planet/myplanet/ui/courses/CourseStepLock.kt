package org.ole.planet.myplanet.ui.courses

object CourseStepLock {

    fun isPageLocked(page: Int, blockingStepIndex: Int): Boolean {
        return blockingStepIndex >= 0 && page > blockingStepIndex + 1
    }

    fun lastOpenPage(blockingStepIndex: Int, stepCount: Int): Int {
        return if (blockingStepIndex < 0) stepCount else minOf(blockingStepIndex + 1, stepCount)
    }
}
