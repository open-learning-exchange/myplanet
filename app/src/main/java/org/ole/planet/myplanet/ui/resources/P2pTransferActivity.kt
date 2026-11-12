package org.ole.planet.myplanet.ui.resources

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityP2pTransferBinding
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.P2pTransferManager
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class P2pTransferActivity : AppCompatActivity() {
    @Inject
    lateinit var resourcesRepository: ResourcesRepository
    private lateinit var binding: ActivityP2pTransferBinding
    private lateinit var p2pManager: P2pTransferManager
    private var peers = mutableListOf<WifiP2pDevice>()
    private var peerAdapter = PeerAdapter()
    private var isReceiver = false
    private var resourceId: String? = null
    private var hostAddress: String? = null
    private var transferInProgress = false

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    p2pManager.requestPeers { peerList ->
                        peers.clear()
                        peers.addAll(peerList.deviceList)
                        peerAdapter.notifyDataSetChanged()
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        p2pManager.requestConnectionInfo { info ->
                            if (!info.groupFormed || transferInProgress) return@requestConnectionInfo
                            hostAddress = info.groupOwnerAddress?.hostAddress
                            if (!info.isGroupOwner && !isReceiver) {
                                // We are the client, and we want to send
                                startSendProcess()
                            } else if (info.isGroupOwner && isReceiver) {
                                // We are the host and we want to receive
                                startReceiveProcess()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityP2pTransferBinding.inflate(layoutInflater)
        setContentView(binding.root)

        resourceId = intent.getStringExtra("resourceId")
        p2pManager = P2pTransferManager(this)

        binding.rvPeers.layoutManager = LinearLayoutManager(this)
        binding.rvPeers.adapter = peerAdapter

        binding.btnSend.setOnClickListener {
            isReceiver = false
            checkPermissionsAndDiscover()
        }

        binding.btnReceive.setOnClickListener {
            isReceiver = true
            checkPermissionsAndDiscover()
        }
    }

    private fun checkPermissionsAndDiscover() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startDiscovery()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            startDiscovery()
        } else {
            Toast.makeText(this, getString(R.string.p2p_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDiscovery() {
        binding.tvStatus.text = getString(R.string.status_discovering)
        p2pManager.discoverPeers(object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                android.util.Log.d("P2P","Discovery started")
            }
            override fun onFailure(reason: Int) {
                binding.tvStatus.text = getString(R.string.status_discovery_failed, reason)
            }
        })
    }

    private fun startSendProcess() {
        val rId = resourceId ?: return
        val host = hostAddress ?: return
        transferInProgress = true

        lifecycleScope.launch {
            try {
                val library = resourcesRepository.getLibraryItemById(rId) ?: return@launch
                val files = resolveResourceFiles(library)
                if (files.isEmpty()) {
                    Toast.makeText(this@P2pTransferActivity, getString(R.string.file_not_found_locally), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val metadata = JsonObject().apply {
                    addProperty(KEY_VERSION, PROTOCOL_VERSION)
                    add(KEY_LIBRARY, gson.toJsonTree(library))
                }.toString()

                binding.tvStatus.text = getString(R.string.status_sending, library.title)
                binding.pbTransfer.visibility = View.VISIBLE

                val success = p2pManager.sendFiles(host, files, metadata) { progress ->
                    runOnUiThread { binding.pbTransfer.progress = progress }
                }

                binding.tvStatus.text = if (success) getString(R.string.status_sent_success) else getString(R.string.status_send_failed)
            } finally {
                transferInProgress = false
            }
        }
    }

    /**
     * The files that make up [library] on this device: the whole folder for an HTML resource
     * (skipping half-finished downloads), otherwise its single file. Empty if it isn't all here.
     */
    private suspend fun resolveResourceFiles(library: MyLibrary): List<P2pTransferManager.TransferFile> = withContext(Dispatchers.IO) {
        val externalDir = FileUtils.getExternalFilesDir(this@P2pTransferActivity) ?: return@withContext emptyList()
        if (library.mediaType == "HTML") {
            val folder = File(externalDir, "ole/${library.resourceId}")
            if (FileUtils.resolveHtmlEntryFile(folder, library.openWhichFile)?.exists() != true) return@withContext emptyList()
            return@withContext folder.walkTopDown()
                .filter { it.isFile && PARTIAL_DOWNLOAD_SUFFIXES.none { suffix -> it.name.endsWith(suffix) } }
                .map { P2pTransferManager.TransferFile(it.relativeTo(folder).invariantSeparatorsPath, it) }
                .toList()
        }

        val address = library.resourceLocalAddress?.takeIf { it.isNotBlank() }
        val localFile = address?.let { FileUtils.getLibraryFile(externalDir, library.id, it) }
        val file = localFile?.takeIf { it.exists() && it.length() > 0 }
            ?: library.resourceRemoteAddress?.takeIf { it.isNotBlank() }
                ?.let { FileUtils.getSDPathFromUrl(this@P2pTransferActivity, it) }
                ?.takeIf { it.exists() && it.length() > 0 }
        listOfNotNull(file?.let { P2pTransferManager.TransferFile(it.name, it) })
    }

    private fun startReceiveProcess() {
        transferInProgress = true
        binding.tvStatus.text = getString(R.string.status_waiting_receive)
        binding.pbTransfer.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                var received: Pair<String, File>? = null
                val result = p2pManager.receiveFiles({ metadata, receivedDir ->
                    received = metadata to receivedDir
                }) { progress ->
                    runOnUiThread { binding.pbTransfer.progress = progress }
                }
                val imported = received?.let { (metadata, receivedDir) -> importResource(metadata, receivedDir) }
                binding.tvStatus.text = when {
                    result == P2pTransferManager.ReceiveResult.NO_SENDER -> {
                        binding.pbTransfer.visibility = View.GONE
                        getString(R.string.status_receive_no_sender)
                    }
                    result == P2pTransferManager.ReceiveResult.RECEIVED && imported != null ->
                        getString(R.string.status_received_imported)
                    else -> getString(R.string.status_receive_failed)
                }
                if (imported != null) {
                    Toast.makeText(this@P2pTransferActivity, getString(R.string.imported_resource, imported.title), Toast.LENGTH_SHORT).show()
                }
            } finally {
                transferInProgress = false
            }
        }
    }

    private suspend fun importResource(metadataJson: String, receivedDir: File): MyLibrary? {
        val incoming = try {
            val json = gson.fromJson(metadataJson, JsonObject::class.java)
            val libraryJson = json?.get(KEY_LIBRARY)?.takeIf { it.isJsonObject }
            libraryJson?.let { gson.fromJson(it, MyLibrary::class.java) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        if (incoming == null) {
            withContext(Dispatchers.IO) { receivedDir.deleteRecursively() }
            return null
        }
        return resourcesRepository.importReceivedResource(incoming, receivedDir)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        p2pManager.disconnect()
    }

    companion object {
        private const val KEY_VERSION = "version"
        private const val KEY_LIBRARY = "library"
        private const val PROTOCOL_VERSION = 3
        private val PARTIAL_DOWNLOAD_SUFFIXES = listOf(".tmp", ".part", ".tmp.etag", ".tmp.size")
        private val gson = Gson()
    }

    inner class PeerAdapter : RecyclerView.Adapter<PeerAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.row_p2p_peer, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = peers[position]
            holder.name.text = device.deviceName
            holder.details.text = device.deviceAddress
            holder.itemView.setOnClickListener {
                val config = android.net.wifi.p2p.WifiP2pConfig().apply {
                    deviceAddress = device.deviceAddress
                    groupOwnerIntent = if (isReceiver) 15 else 0
                }
                binding.tvStatus.text = getString(R.string.status_connecting, device.deviceName)
                p2pManager.connect(config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        android.util.Log.d("P2P","Connection initiated")
                    }
                    override fun onFailure(reason: Int) {
                        binding.tvStatus.text = getString(R.string.status_connection_failed, reason)
                    }
                })
            }
        }

        override fun getItemCount() = peers.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.tv_device_name)
            val details: TextView = view.findViewById(R.id.tv_device_details)
        }
    }
}
