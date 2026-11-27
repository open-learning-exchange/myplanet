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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.ole.planet.myplanet.services.P2pTransferManager.ReceiveResult
import org.ole.planet.myplanet.services.P2pTransferManager.TransferFile

class P2pTransferManagerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var manager: P2pTransferManager
    private lateinit var cacheDir: File

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        val cache = temporaryFolder.newFolder("cache").also { cacheDir = it }
        val context: Context = mockk(relaxed = true) {
            every { this@mockk.cacheDir } returns cache
        }
        manager = P2pTransferManager(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `receiveFiles gives up when no sender connects in time`() = runBlocking {
        val result = withTimeout(5_000) {
            manager.receiveFiles({ _, _ -> }, acceptTimeoutMs = 200) { }
        }

        assertEquals(ReceiveResult.NO_SENDER, result)
    }

    @Test
    fun `cancelling receiveFiles stops the wait immediately`() = runBlocking {
        val receive = async { manager.receiveFiles({ _, _ -> }, acceptTimeoutMs = 60_000) { } }
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
        val metadata = """{"version":3,"library":{"id":"res1","title":"Café"}}"""

        val (receivedMetadata, receivedDir) = transfer(listOf(TransferFile("book.pdf", source)), metadata)

        assertEquals(metadata, receivedMetadata)
        assertTrue(source.readBytes().contentEquals(File(receivedDir, "book.pdf").readBytes()))
    }

    @Test
    fun `a folder of files arrives with its layout intact`() = runBlocking {
        val site = temporaryFolder.newFolder("site")
        val files = mapOf(
            "index.html" to "<html>home</html>",
            "css/style.css" to "body{}",
            "img/deep/logo.png" to "png-bytes",
            "empty.txt" to ""
        ).map { (path, content) ->
            TransferFile(path, File(site, path).apply { parentFile?.mkdirs(); writeText(content) })
        }

        val (_, receivedDir) = transfer(files, "{}")

        files.forEach { assertEquals(it.file.readText(), File(receivedDir, it.relativePath).readText()) }
    }

    @Test
    fun `a path that escapes the receive folder is refused`() = runBlocking {
        val evil = temporaryFolder.newFile("evil.sh").apply { writeText("rm -rf") }
        val received = CompletableDeferred<File>()

        withTimeout(10_000) {
            val receive = async {
                manager.receiveFiles({ _, dir -> received.complete(dir) }, acceptTimeoutMs = 5_000) { }
            }
            delay(200)
            manager.sendFiles("127.0.0.1", listOf(TransferFile("../../evil.sh", evil)), "{}") { }

            assertEquals(ReceiveResult.FAILED, receive.await())
        }
        assertFalse(received.isCompleted)
        assertTrue(cacheDir.listFiles().isNullOrEmpty())
    }

    private suspend fun transfer(files: List<TransferFile>, metadata: String): Pair<String, File> {
        val received = CompletableDeferred<Pair<String, File>>()
        withTimeout(10_000) {
            val receive = async {
                manager.receiveFiles({ meta, dir -> received.complete(meta to dir) }, acceptTimeoutMs = 5_000) { }
            }
            delay(200)
            assertTrue(manager.sendFiles("127.0.0.1", files, metadata) { })
            assertEquals(ReceiveResult.RECEIVED, receive.await())
        }
        return received.await()
    }

    @Test
    fun `a sender that connects but sends nothing valid is a failure, not a missing sender`() = runBlocking {
        withTimeout(10_000) {
            val receive = async { manager.receiveFiles({ _, _ -> }, acceptTimeoutMs = 5_000) { } }
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
        assertEquals(ReceiveResult.NO_SENDER, manager.receiveFiles({ _, _ -> }, acceptTimeoutMs = 100) { })
        assertEquals(ReceiveResult.NO_SENDER, manager.receiveFiles({ _, _ -> }, acceptTimeoutMs = 100) { })
    }
}
