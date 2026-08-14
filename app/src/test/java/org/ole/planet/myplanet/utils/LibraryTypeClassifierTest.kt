package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.MyLibrary

class LibraryTypeClassifierTest {

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
        val result = LibraryTypeClassifier.classify(library(localAddress = "/storage/doc.pdf"))
        assertEquals(LibraryType.PDF, result)
    }

    @Test
    fun classify_videoExtension_returnsVideo() {
        assertEquals(LibraryType.VIDEO, LibraryTypeClassifier.classify(library(localAddress = "/storage/clip.mp4")))
        assertEquals(LibraryType.VIDEO, LibraryTypeClassifier.classify(library(localAddress = "/storage/clip.webm")))
    }

    @Test
    fun classify_audioExtension_returnsAudio() {
        assertEquals(LibraryType.AUDIO, LibraryTypeClassifier.classify(library(localAddress = "/storage/song.mp3")))
        assertEquals(LibraryType.AUDIO, LibraryTypeClassifier.classify(library(localAddress = "/storage/song.ogg")))
    }

    @Test
    fun classify_unknownExtension_defaultsToBook() {
        val result = LibraryTypeClassifier.classify(library(localAddress = "/storage/book.epub"))
        assertEquals(LibraryType.BOOK, result)
    }

    @Test
    fun classify_noExtensionButVideoMediaType_returnsVideo() {
        val result = LibraryTypeClassifier.classify(
            library(localAddress = "/storage/noext", mediaType = "video/mp4")
        )
        assertEquals(LibraryType.VIDEO, result)
    }

    @Test
    fun classify_noExtensionButAudioMediaType_returnsAudio() {
        val result = LibraryTypeClassifier.classify(
            library(localAddress = "/storage/noext", mediaType = "audio/mpeg")
        )
        assertEquals(LibraryType.AUDIO, result)
    }

    @Test
    fun classify_nullLocalAddress_fallsBackToRemoteAddress() {
        val result = LibraryTypeClassifier.classify(
            library(localAddress = null, remoteAddress = "https://example.org/video.mkv")
        )
        assertEquals(LibraryType.VIDEO, result)
    }

    @Test
    fun classify_noAddressAndNoMediaType_defaultsToBook() {
        val result = LibraryTypeClassifier.classify(library())
        assertEquals(LibraryType.BOOK, result)
    }

    @Test
    fun classify_extensionCaseInsensitive() {
        val result = LibraryTypeClassifier.classify(library(localAddress = "/storage/doc.PDF"))
        assertEquals(LibraryType.PDF, result)
    }
}
