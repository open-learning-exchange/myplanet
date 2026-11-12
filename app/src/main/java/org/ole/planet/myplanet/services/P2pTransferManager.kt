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
        private const val MAX_FILES = 10_000
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

    /** One file in a transfer, stored at [relativePath] under the resource's folder on the receiver. */
    data class TransferFile(val relativePath: String, val file: File)

    /**
     * Sends [metadata] followed by [files]. Wire format: metadata length + UTF-8 metadata, file
     * count, total bytes, then for each file its relative path (writeUTF), length and contents.
     */
    suspend fun sendFiles(host: String, files: List<TransferFile>, metadata: String, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext false
        val socket = Socket()

        val closeOnCancel = closeOnCancel { socket.closeQuietly() }
        try {
            socket.soTimeout = READ_TIMEOUT_MS
            socket.connect(InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS)

            val dataOutputStream = DataOutputStream(socket.getOutputStream().buffered())
            val metadataBytes = metadata.toByteArray(Charsets.UTF_8)
            dataOutputStream.writeInt(metadataBytes.size)
            dataOutputStream.write(metadataBytes)

            val sizes = files.map { it.file.length() }
            val totalBytes = sizes.sum()
            dataOutputStream.writeInt(files.size)
            dataOutputStream.writeLong(totalBytes)

            val buffer = ByteArray(BUFFER_SIZE)
            var totalSent = 0L
            files.forEachIndexed { index, transferFile ->
                val size = sizes[index]
                dataOutputStream.writeUTF(transferFile.relativePath)
                dataOutputStream.writeLong(size)
                transferFile.file.inputStream().use { inputStream ->
                    var remaining = size
                    while (remaining > 0) {
                        coroutineContext.ensureActive()
                        val len = inputStream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (len == -1) throw IOException("${transferFile.relativePath} shrank while sending")
                        dataOutputStream.write(buffer, 0, len)
                        remaining -= len
                        totalSent += len
                        if (totalBytes > 0) onProgress(((totalSent * 100) / totalBytes).toInt())
                    }
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

    suspend fun receiveFiles(
        onReceived: (String, File) -> Unit,
        acceptTimeoutMs: Int = ACCEPT_TIMEOUT_MS,
        onProgress: (Int) -> Unit
    ): ReceiveResult = withContext(Dispatchers.IO) {
        val serverSocket = ServerSocket()
        val client = AtomicReference<Socket?>(null)

        val closeOnCancel = closeOnCancel {
            client.get()?.closeQuietly()
            serverSocket.closeQuietly()
        }
        var receiveDir: File? = null
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

            val fileCount = dataInputStream.readInt()
            val totalBytes = dataInputStream.readLong()
            if (fileCount !in 1..MAX_FILES || totalBytes < 0) return@withContext ReceiveResult.FAILED

            val dir = File(context.cacheDir, "p2p_received_${System.currentTimeMillis()}").also { receiveDir = it }
            if (!dir.mkdirs()) return@withContext ReceiveResult.FAILED

            val buffer = ByteArray(BUFFER_SIZE)
            var totalReceived = 0L
            repeat(fileCount) {
                val target = resolveInside(dir, dataInputStream.readUTF()) ?: return@withContext ReceiveResult.FAILED
                val size = dataInputStream.readLong()
                if (size < 0 || totalReceived + size > totalBytes) return@withContext ReceiveResult.FAILED
                target.parentFile?.mkdirs()
                target.outputStream().use { outputStream ->
                    var remaining = size
                    while (remaining > 0) {
                        coroutineContext.ensureActive()
                        val len = dataInputStream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (len == -1) return@withContext ReceiveResult.FAILED
                        outputStream.write(buffer, 0, len)
                        remaining -= len
                        totalReceived += len
                        if (totalBytes > 0) onProgress(((totalReceived * 100) / totalBytes).toInt())
                    }
                }
            }

            receiveDir = null
            onReceived(metadata, dir)
            ReceiveResult.RECEIVED
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "P2P receive failed", e)
            ReceiveResult.FAILED
        } finally {
            closeOnCancel.cancel()
            client.get()?.closeQuietly()
            serverSocket.closeQuietly()
            receiveDir?.deleteRecursively()
        }
    }

    /** Resolves a path sent by the other device, refusing anything that would land outside [dir]. */
    private fun resolveInside(dir: File, relativePath: String): File? {
        if (relativePath.isBlank() || relativePath.startsWith("/") || relativePath.startsWith("\\")) return null
        if (relativePath.split('/', '\\').any { it == ".." }) return null
        val base = dir.canonicalFile
        val resolved = File(base, relativePath).canonicalFile
        return resolved.takeIf { it.path.startsWith(base.path + File.separator) }
    }
    
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
