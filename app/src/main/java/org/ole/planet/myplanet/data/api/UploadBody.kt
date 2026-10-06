package org.ole.planet.myplanet.data.api

/** A request body described without any transport types; the [PlanetApi] implementation encodes it. */
sealed interface UploadBody {
    val contentType: String?

    /** The bytes of the file at [path], streamed from disk when the request is written. */
    class FileContent(val path: String, override val contentType: String?) : UploadBody

    /** [text] encoded with the charset of [contentType] (UTF-8 when it names none). */
    class TextContent(val text: String, override val contentType: String?) : UploadBody
}
