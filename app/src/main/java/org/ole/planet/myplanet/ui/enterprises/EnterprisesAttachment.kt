package org.ole.planet.myplanet.ui.enterprises

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.TimeProvider

internal suspend fun readEnterpriseAttachment(
    context: Context,
    uri: Uri?,
    timeProvider: TimeProvider,
    dispatcherProvider: DispatcherProvider
): Pair<String?, ByteArray?> {
    if (uri == null) return null to null
    return withContext(dispatcherProvider.io) {
        FileUtils.getDisplayName(context, uri, timeProvider) to FileUtils.readBytesFromUri(context, uri)
    }
}
