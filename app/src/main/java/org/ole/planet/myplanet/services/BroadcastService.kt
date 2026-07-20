package org.ole.planet.myplanet.services

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
open class BroadcastService @Inject constructor() {
    private val _events = MutableSharedFlow<Intent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    // Download progress/completion is current *state*, not a one-shot event: a collector that
    // (re)subscribes after missing the last emission - e.g. the app was backgrounded right as a
    // download finished, or a fragment's view was torn down and recreated on rotation - must
    // still be able to observe it. `events` has replay = 0, so a collector that wasn't actively
    // subscribed at the exact moment of emission loses that intent forever. MESSAGE_PROGRESS
    // intents are therefore tracked here instead, where StateFlow always replays its latest
    // value to new subscribers. Other action types (retry countdowns, one-shot "resource not
    // found" dialogs) intentionally stay on `events` only, since replaying those on every
    // resubscribe would incorrectly re-trigger a dialog/toast the user already saw.
    private val _latestDownloadProgress = MutableStateFlow<Intent?>(null)
    val latestDownloadProgress: StateFlow<Intent?> = _latestDownloadProgress.asStateFlow()

    open suspend fun sendBroadcast(intent: Intent) {
        if (intent.action == DownloadService.MESSAGE_PROGRESS) {
            _latestDownloadProgress.value = intent
        } else {
            _events.emit(intent)
        }
    }

    fun trySendBroadcast(intent: Intent): Boolean {
        if (intent.action == DownloadService.MESSAGE_PROGRESS) {
            _latestDownloadProgress.value = intent
            return true
        }
        return _events.tryEmit(intent)
    }
}
