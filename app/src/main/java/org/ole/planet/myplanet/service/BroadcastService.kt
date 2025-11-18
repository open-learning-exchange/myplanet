package org.ole.planet.myplanet.service

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class BroadcastService @Inject constructor() {
    private val _events = MutableSharedFlow<Intent>()
    val events = _events.asSharedFlow()

    suspend fun sendBroadcast(intent: Intent) {
        _events.emit(intent)
    }
}
