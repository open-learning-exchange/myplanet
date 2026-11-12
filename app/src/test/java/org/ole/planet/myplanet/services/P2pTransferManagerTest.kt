package org.ole.planet.myplanet.services

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.ole.planet.myplanet.services.P2pTransferManager.ReceiveResult

class P2pTransferManagerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var manager: P2pTransferManager

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        val cacheDir = temporaryFolder.newFolder("cache")
        val context: Context = mockk(relaxed = true) {
            every { this@mockk.cacheDir } returns cacheDir
        }
        manager = P2pTransferManager(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `receiveFile gives up when no sender connects in time`() = runBlocking {
        val result = withTimeout(5_000) {
            manager.receiveFile({ _, _ -> }, acceptTimeoutMs = 200) { }
        }

        assertEquals(ReceiveResult.NO_SENDER, result)
    }

    @Test
    fun `cancelling receiveFile stops the wait immediately`() = runBlocking {
        val receive = async { manager.receiveFile({ _, _ -> }, acceptTimeoutMs = 60_000) { } }
        delay(200)

        withTimeout(2_000) {
            receive.cancel()
            receive.join()
        }

        assertTrue(receive.isCancelled)
    }

    @Test
    fun `a sent file arrives intact with its metadata`() = runBlocking {
        val source = temporaryFolder.newFile("book.pdf").apply { writeBytes(ByteArray(20_000) { (it % 251).toByte() }) }
        val metadata = """{"version":2,"library":{"id":"res1","title":"Café"}}"""
        val received = CompletableDeferred<Pair<String, File>>()

        withTimeout(10_000) {
            val receive = async {
                manager.receiveFile({ meta, file -> received.complete(meta to file) }, acceptTimeoutMs = 5_000) { }
            }
            delay(200)
            val sent = manager.sendFile("127.0.0.1", source, metadata) { }

            assertTrue(sent)
            assertEquals(ReceiveResult.RECEIVED, receive.await())
        }

        val (receivedMetadata, receivedFile) = received.await()
        assertEquals(metadata, receivedMetadata)
        assertTrue(source.readBytes().contentEquals(receivedFile.readBytes()))
    }

    @Test
    fun `a sender that connects but sends nothing valid is a failure, not a missing sender`() = runBlocking {
        withTimeout(10_000) {
            val receive = async { manager.receiveFile({ _, _ -> }, acceptTimeoutMs = 5_000) { } }
            delay(200)
            java.net.Socket("127.0.0.1", P2pTransferManager.PORT).use { socket ->
                java.io.DataOutputStream(socket.getOutputStream()).writeInt(-1)
            }

            assertEquals(ReceiveResult.FAILED, receive.await())
        }
    }

    @Test
    fun `the port can be reused straight after a timed-out wait`() = runBlocking {
        // Both calls reach accept() and time out, rather than the second failing to bind the port.
        assertEquals(ReceiveResult.NO_SENDER, manager.receiveFile({ _, _ -> }, acceptTimeoutMs = 100) { })
        assertEquals(ReceiveResult.NO_SENDER, manager.receiveFile({ _, _ -> }, acceptTimeoutMs = 100) { })
    }
}
