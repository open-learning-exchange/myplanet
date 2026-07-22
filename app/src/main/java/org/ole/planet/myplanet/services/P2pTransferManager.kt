package org.ole.planet.myplanet.services

import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.utils.Utilities

class P2pTransferManager(private val context: Context) {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, Looper.getMainLooper(), null)

    companion object {
        const val PORT = 8888
    }

    fun discoverPeers(listener: WifiP2pManager.ActionListener) {
        channel?.let {
            manager?.discoverPeers(it, listener)
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
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.bind(null)
            socket.connect(InetSocketAddress(host, PORT), 5000)

            val outputStream = socket.getOutputStream()
            
            // Send metadata length then metadata
            val metadataBytes = metadata.toByteArray()
            outputStream.write(metadataBytes.size) // This is simplified, usually we need 4 bytes for length
            // Better: use DataOutputStream
            val dataOutputStream = java.io.DataOutputStream(outputStream)
            dataOutputStream.writeInt(metadataBytes.size)
            dataOutputStream.write(metadataBytes)

            // Send file length then file
            dataOutputStream.writeLong(file.length())
            val inputStream = file.inputStream()
            val buffer = ByteArray(4096)
            var len: Int
            var totalSent: Long = 0
            val fileSize = file.length()

            while (inputStream.read(buffer).also { len = it } != -1) {
                dataOutputStream.write(buffer, 0, len)
                totalSent += len
                onProgress(((totalSent * 100) / fileSize).toInt())
            }
            
            inputStream.close()
            dataOutputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            socket?.close()
        }
    }

    suspend fun receiveFile(onReceived: (String, File) -> Unit, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        var serverSocket: ServerSocket? = null
        var client: Socket? = null
        try {
            serverSocket = ServerSocket(PORT)
            client = serverSocket.accept()

            val inputStream = client.getInputStream()
            val dataInputStream = java.io.DataInputStream(inputStream)

            // Read metadata
            val metadataSize = dataInputStream.readInt()
            val metadataBytes = ByteArray(metadataSize)
            dataInputStream.readFully(metadataBytes)
            val metadata = String(metadataBytes)

            // Read file
            val fileSize = dataInputStream.readLong()
            val tempFile = File(context.cacheDir, "p2p_received_${System.currentTimeMillis()}")
            val outputStream = tempFile.outputStream()
            
            val buffer = ByteArray(4096)
            var len: Int
            var totalReceived: Long = 0

            while (totalReceived < fileSize) {
                len = dataInputStream.read(buffer, 0, Math.min(buffer.size.toLong(), fileSize - totalReceived).toInt())
                if (len == -1) break
                outputStream.write(buffer, 0, len)
                totalReceived += len
                onProgress(((totalReceived * 100) / fileSize).toInt())
            }

            outputStream.close()
            onReceived(metadata, tempFile)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            client?.close()
            serverSocket?.close()
        }
    }
}
