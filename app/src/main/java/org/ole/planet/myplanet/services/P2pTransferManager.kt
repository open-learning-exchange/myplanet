package org.ole.planet.myplanet.services

import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class P2pTransferManager(private val context: Context) {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, Looper.getMainLooper(), null)

    companion object {
        const val PORT = 8888
        private const val TAG = "P2pTransferManager"
        private const val CONNECT_TIMEOUT_MS = 5_000
        const val ACCEPT_TIMEOUT_MS = 120_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 8 * 1024
        private const val MAX_METADATA_BYTES = 1024 * 1024
    }

    fun discoverPeers(listener: WifiP2pManager.ActionListener) {
        channel?.let {
            manager?.discoverPeers(it, listener)
        }
    }

    fun requestPeers(listener: WifiP2pManager.PeerListListener) {
        channel?.let {
            manager?.requestPeers(it, listener)
        }
    }

    fun requestConnectionInfo(listener: WifiP2pManager.ConnectionInfoListener) {
        channel?.let {
            manager?.requestConnectionInfo(it, listener)
        }
    }

    fun connect(config: WifiP2pConfig, listener: WifiP2pManager.ActionListener) {
        channel?.let {
            manager?.connect(it, config, listener)
        }
    }

    fun disconnect() {
        channel?.let {
            manager?.removeGroup(it, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    android.util.Log.d("P2P", "P2P disconnected")
                }
                override fun onFailure(reason: Int) {
                    android.util.Log.d("P2P", "P2P disconnect failed: $reason")
                }
            })
        }
    }

    suspend fun sendFile(host: String, file: File, metadata: String, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val socket = Socket()
        // Closing the socket unblocks a connect or write stuck on a peer that stopped responding.
        val closeOnCancel = closeOnCancel { socket.closeQuietly() }
        try {
            socket.soTimeout = READ_TIMEOUT_MS
            socket.connect(InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS)

            val dataOutputStream = DataOutputStream(socket.getOutputStream().buffered())
            val metadataBytes = metadata.toByteArray(Charsets.UTF_8)
            dataOutputStream.writeInt(metadataBytes.size)
            dataOutputStream.write(metadataBytes)

            val fileSize = file.length()
            dataOutputStream.writeLong(fileSize)
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var totalSent = 0L
                var len: Int
                while (inputStream.read(buffer).also { len = it } != -1) {
                    coroutineContext.ensureActive()
                    dataOutputStream.write(buffer, 0, len)
                    totalSent += len
                    if (fileSize > 0) onProgress(((totalSent * 100) / fileSize).toInt())
                }
            }
            dataOutputStream.flush()
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "P2P send failed", e)
            false
        } finally {
            closeOnCancel.cancel()
            socket.closeQuietly()
        }
    }

    enum class ReceiveResult { RECEIVED, NO_SENDER, FAILED }

    /**
     * Waits up to [acceptTimeoutMs] for a sender to connect, then receives one file. Returns
     * [ReceiveResult.NO_SENDER] if nobody connects in time, and [ReceiveResult.FAILED] if the sender
     * stalls for [READ_TIMEOUT_MS] or the transfer is incomplete. Cancelling the calling coroutine
     * (e.g. leaving the screen) stops the wait at once.
     */
    suspend fun receiveFile(
        onReceived: (String, File) -> Unit,
        acceptTimeoutMs: Int = ACCEPT_TIMEOUT_MS,
        onProgress: (Int) -> Unit
    ): ReceiveResult = withContext(Dispatchers.IO) {
        val serverSocket = ServerSocket()
        val client = AtomicReference<Socket?>(null)
        // accept() and read() ignore coroutine cancellation; closing the sockets is what unblocks them.
        val closeOnCancel = closeOnCancel {
            client.get()?.closeQuietly()
            serverSocket.closeQuietly()
        }
        var tempFile: File? = null
        try {
            serverSocket.reuseAddress = true
            serverSocket.soTimeout = acceptTimeoutMs
            serverSocket.bind(InetSocketAddress(PORT))
            val socket = try {
                serverSocket.accept().also { client.set(it) }
            } catch (e: SocketTimeoutException) {
                Log.i(TAG, "P2P receive timed out")
                return@withContext ReceiveResult.NO_SENDER
            }
            socket.soTimeout = READ_TIMEOUT_MS

            val dataInputStream = DataInputStream(socket.getInputStream().buffered())

            val metadataSize = dataInputStream.readInt()
            if (metadataSize !in 0..MAX_METADATA_BYTES) return@withContext ReceiveResult.FAILED
            val metadataBytes = ByteArray(metadataSize)
            dataInputStream.readFully(metadataBytes)
            val metadata = String(metadataBytes, Charsets.UTF_8)

            val fileSize = dataInputStream.readLong()
            if (fileSize < 0) return@withContext ReceiveResult.FAILED
            val file = File(context.cacheDir, "p2p_received_${System.currentTimeMillis()}").also { tempFile = it }

            var totalReceived = 0L
            file.outputStream().use { outputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (totalReceived < fileSize) {
                    coroutineContext.ensureActive()
                    val len = dataInputStream.read(buffer, 0, minOf(buffer.size.toLong(), fileSize - totalReceived).toInt())
                    if (len == -1) break
                    outputStream.write(buffer, 0, len)
                    totalReceived += len
                    if (fileSize > 0) onProgress(((totalReceived * 100) / fileSize).toInt())
                }
            }

            if (totalReceived < fileSize) return@withContext ReceiveResult.FAILED
            tempFile = null
            onReceived(metadata, file)
            ReceiveResult.RECEIVED
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "P2P receive failed", e)
            ReceiveResult.FAILED
        } finally {
            closeOnCancel.cancel()
            client.get()?.closeQuietly()
            serverSocket.closeQuietly()
            tempFile?.delete()
        }
    }

    /**
     * Starts a child that runs [onCancel] on another IO thread as soon as the caller is cancelled,
     * while the caller's thread is still blocked in socket I/O. Cancel the returned job when done.
     */
    private fun CoroutineScope.closeOnCancel(onCancel: () -> Unit): Job = launch {
        try {
            awaitCancellation()
        } finally {
            onCancel()
        }
    }

    private fun Closeable.closeQuietly() {
        try {
            close()
        } catch (_: IOException) {
        }
    }
}
