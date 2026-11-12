package org.ole.planet.myplanet.utils

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.withContext

data class UriAttachment(val name: String?, val bytes: ByteArray?)

class AttachmentReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun read(uri: Uri?): UriAttachment {
        if (uri == null) return UriAttachment(null, null)
        return withContext(dispatcherProvider.io) {
            UriAttachment(
                FileUtils.getDisplayName(context, uri, timeProvider),
                FileUtils.readBytesFromUri(context, uri)
            )
        }
    }
}
