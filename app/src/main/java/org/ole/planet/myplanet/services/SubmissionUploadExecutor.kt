package org.ole.planet.myplanet.services

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.utils.DispatcherProvider

@Singleton
class SubmissionUploadExecutor @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider
) {
    fun execute(block: suspend CoroutineScope.() -> Unit) {
        scope.launch(dispatcherProvider.io, block = block)
    }
}
