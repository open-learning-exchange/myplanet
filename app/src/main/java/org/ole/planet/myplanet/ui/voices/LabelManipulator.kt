package org.ole.planet.myplanet.ui.voices

import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

interface LabelManipulator {
    suspend fun addLabel(newsId: String, label: String)
    suspend fun removeLabel(newsId: String, label: String)
}

class DefaultLabelManipulator(
    private val voicesRepository: VoicesRepository,
    private val dispatcherProvider: DispatcherProvider
) : LabelManipulator {
    override suspend fun addLabel(newsId: String, label: String) {
        voicesRepository.addLabel(newsId, label)
    }

    override suspend fun removeLabel(newsId: String, label: String) {
        voicesRepository.removeLabel(newsId, label)
    }
}
