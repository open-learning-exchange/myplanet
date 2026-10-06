package org.ole.planet.myplanet.data.api

import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import okio.BufferedSource

/**
 * What [KtorPlanetApi] cannot do in common code: canonicalise a URL the way the platform's HTTP
 * stack will send it, open a file lazily for an upload, and expose a response channel as a
 * blocking okio source for [StreamBody]. Keeping these behind one seam leaves [KtorPlanetApi]
 * free of `java.*` so it can move to `:shared`.
 */
interface KtorPlatform {
    /**
     * [url], resolved against [base] when it is relative, exactly as the platform's HTTP stack
     * puts it on the wire (percent-encoding, host and port normalised), or null when it cannot
     * be resolved to an http(s) URL.
     */
    fun canonicalUrl(base: String, url: String): String?

    /** The bytes of the file at [path], read from disk only while the request is written. */
    fun fileContent(path: String): OutgoingContent

    /** A blocking view of [channel]; closing the returned source cancels the channel. */
    fun bufferedSource(channel: ByteReadChannel): BufferedSource
}
