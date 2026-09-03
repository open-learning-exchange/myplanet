package org.ole.planet.myplanet.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> TestScope.collectEmissions(flow: Flow<T>): List<T> {
    val emissions = mutableListOf<T>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) {
        flow.toList(emissions)
    }
    advanceUntilIdle()
    job.cancel()
    return emissions
}
