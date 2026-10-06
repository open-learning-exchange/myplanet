package org.ole.planet.myplanet.data.api

import okio.Buffer
import okio.BufferedSource

/** An in-memory [StreamBody] for tests; [contentLength] defaults to the real byte count. */
class TestStreamBody(
    bytes: ByteArray,
    override val contentLength: Long = bytes.size.toLong()
) : StreamBody {
    private val buffer = Buffer().write(bytes)

    var closed = false
        private set

    override fun source(): BufferedSource = buffer

    override fun close() {
        closed = true
        buffer.close()
    }
}

fun String.toStreamBody(): TestStreamBody = TestStreamBody(toByteArray())

fun ByteArray.toStreamBody(): TestStreamBody = TestStreamBody(this)
