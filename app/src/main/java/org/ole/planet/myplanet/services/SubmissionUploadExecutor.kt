package org.ole.planet.myplanet.services

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.di.ApplicationScope

@Singleton
class SubmissionUploadExecutor @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope
) {
    fun execute(block: suspend CoroutineScope.() -> Unit) {
        scope.launch(Dispatchers.IO, block = block)
    }
}
