package org.ole.planet.myplanet.data.api

import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.File
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import okio.source

/** [KtorPlatform] on the JVM: URLs through OkHttp's `HttpUrl`, files through `java.io.File`. */
object JvmKtorPlatform : KtorPlatform {
    /** Retrofit resolves an absolute `@Url` to exactly this `HttpUrl`, so both send the same bytes. */
    override fun canonicalUrl(url: String): String? = url.toHttpUrlOrNull()?.toString()

    override fun fileContent(path: String): OutgoingContent = FileContent(File(path))

    override fun bufferedSource(channel: ByteReadChannel): BufferedSource = EngineErrorSource(channel.toInputStream().source()).buffer()

    /** Reads fail with the exception OkHttp threw (a read timeout stays a SocketTimeoutException). */
    private class EngineErrorSource(delegate: Source) : ForwardingSource(delegate) {
        override fun read(sink: Buffer, byteCount: Long): Long = try {
            super.read(sink, byteCount)
        } catch (e: IOException) {
            throw e.withoutEngineWrapper() as? IOException ?: e
        }
    }

    /**
     * Same length and laziness as OkHttp's `File.asRequestBody`: the length is the file's size
     * when the request is built, every write (a retried request included) re-opens the file, and
     * a file that cannot be opened fails the call with its FileNotFoundException instead of
     * sending an empty body. The Content-Type travels as a request header, so it is left null.
     */
    private class FileContent(private val file: File) : OutgoingContent.ReadChannelContent() {
        override val contentLength: Long get() = file.length()

        override fun readFrom(): ByteReadChannel = file.inputStream().toByteReadChannel()
    }
}
