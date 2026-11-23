package org.ole.planet.myplanet.ui.resources

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.MyLibrary

class ResourcesMediaTypeTest {

    private fun library(
        localAddress: String? = null,
        remoteAddress: String? = null,
        mediaType: String? = null
    ) = MyLibrary().apply {
        resourceLocalAddress = localAddress
        resourceRemoteAddress = remoteAddress
        this.mediaType = mediaType
    }

    @Test
    fun classify_pdfExtension_returnsPdf() {
        val result = ResourcesMediaType.classify(library(localAddress = "/storage/doc.pdf"))
        assertEquals(ResourcesMediaType.PDF, result)
    }

    @Test
    fun classify_videoExtension_returnsVideo() {
        assertEquals(ResourcesMediaType.VIDEO, ResourcesMediaType.classify(library(localAddress = "/storage/clip.mp4")))
        assertEquals(ResourcesMediaType.VIDEO, ResourcesMediaType.classify(library(localAddress = "/storage/clip.webm")))
    }

    @Test
    fun classify_audioExtension_returnsAudio() {
        assertEquals(ResourcesMediaType.AUDIO, ResourcesMediaType.classify(library(localAddress = "/storage/song.mp3")))
        assertEquals(ResourcesMediaType.AUDIO, ResourcesMediaType.classify(library(localAddress = "/storage/song.ogg")))
    }

    @Test
    fun classify_unknownExtension_defaultsToBook() {
        val result = ResourcesMediaType.classify(library(localAddress = "/storage/book.epub"))
        assertEquals(ResourcesMediaType.BOOK, result)
    }

    @Test
    fun classify_noExtensionButVideoMediaType_returnsVideo() {
        val result = ResourcesMediaType.classify(
            library(localAddress = "/storage/noext", mediaType = "video/mp4")
        )
        assertEquals(ResourcesMediaType.VIDEO, result)
    }

    @Test
    fun classify_noExtensionButAudioMediaType_returnsAudio() {
        val result = ResourcesMediaType.classify(
            library(localAddress = "/storage/noext", mediaType = "audio/mpeg")
        )
        assertEquals(ResourcesMediaType.AUDIO, result)
    }

    @Test
    fun classify_nullLocalAddress_fallsBackToRemoteAddress() {
        val result = ResourcesMediaType.classify(
            library(localAddress = null, remoteAddress = "https://example.org/video.mkv")
        )
        assertEquals(ResourcesMediaType.VIDEO, result)
    }

    @Test
    fun classify_noAddressAndNoMediaType_defaultsToBook() {
        val result = ResourcesMediaType.classify(library())
        assertEquals(ResourcesMediaType.BOOK, result)
    }

    @Test
    fun classify_extensionCaseInsensitive() {
        val result = ResourcesMediaType.classify(library(localAddress = "/storage/doc.PDF"))
        assertEquals(ResourcesMediaType.PDF, result)
    }
}
