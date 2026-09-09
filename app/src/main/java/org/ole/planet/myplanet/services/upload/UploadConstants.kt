package org.ole.planet.myplanet.services.upload

object UploadConstants {
    const val BATCH_SIZE = 50
}

internal inline fun <T> Iterable<T>.processInBatches(action: (List<T>) -> Unit) {
    chunked(UploadConstants.BATCH_SIZE).forEach(action)
}
