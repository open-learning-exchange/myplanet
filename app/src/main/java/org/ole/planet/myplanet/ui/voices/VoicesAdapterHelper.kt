package org.ole.planet.myplanet.ui.voices

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.Utilities

object VoicesAdapterHelper {
    fun createOnAnimateTyping(dispatcherProvider: DispatcherProvider): (String, (String) -> Unit, () -> Unit) -> (() -> Unit) {
        return { response, onUpdate, onComplete ->
            val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)
            val job = scope.launch {
                var currentIndex = 0
                while (currentIndex < response.length) {
                    if (!isActive) return@launch
                    onUpdate(response.substring(0, currentIndex + 1))
                    currentIndex++
                    delay(10L)
                }
                onComplete()
            }
            val cancelJob: () -> Unit = { scope.cancel() }
            cancelJob
        }
    }

    fun handleShareNewsResult(context: Context, result: Result<Unit>) {
        if (result.isSuccess) {
            Utilities.toast(context, context.getString(R.string.shared_to_community))
        } else {
            Utilities.toast(context, "Failed to share news")
        }
    }
}
