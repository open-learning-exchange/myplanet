package org.ole.planet.myplanet.services

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
open class BroadcastService @Inject constructor() {
    private val _events = MutableSharedFlow<Intent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    open suspend fun sendBroadcast(intent: Intent) {
        _events.emit(intent)
    }

    fun trySendBroadcast(intent: Intent): Boolean {
        return _events.tryEmit(intent)
    }
}
