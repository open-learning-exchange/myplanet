package org.ole.planet.myplanet.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit Rule to set up coroutines test dispatchers.
 * 
 * This rule replaces all Dispatchers.Main with a test dispatcher for the duration of tests,
 * ensuring deterministic execution of coroutines in unit tests.
 * 
 * Usage:
 * ```
 * @get:Rule
 * val mainDispatcherRule = MainDispatcherRule()
 * ```
 * 
 * For advanced scenarios requiring controlled execution:
 * ```
 * val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher(testScheduler))
 * ```
 * 
 * @param testDispatcher The test dispatcher to use. Defaults to UnconfinedTestDispatcher
 *                       which executes coroutines immediately without requiring advanceUntilIdle().
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
