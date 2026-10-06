package org.ole.planet.myplanet.utils

import kotlinx.coroutines.CoroutineDispatcher

interface DispatcherProvider {
    val main: CoroutineDispatcher
    val mainImmediate: CoroutineDispatcher
        get() = main
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}
