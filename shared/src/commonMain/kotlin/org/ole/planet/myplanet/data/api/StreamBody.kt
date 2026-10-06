package org.ole.planet.myplanet.data.api

import okio.BufferedSource

/**
 * A response body read incrementally from the network. It is never buffered whole: callers
 * pull bytes from [source] and must [close] it (closing the source also closes the body).
 */
interface StreamBody : AutoCloseable {
    /** Byte count announced by the server, or -1 when unknown. */
    val contentLength: Long

    fun source(): BufferedSource
}
