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
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityP2pTransferBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.services.P2pTransferManager
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class P2pTransferActivity : AppCompatActivity() {
    private lateinit var binding: ActivityP2pTransferBinding
    private lateinit var p2pManager: P2pTransferManager
    private var peers = mutableListOf<WifiP2pDevice>()
    private var peerAdapter = PeerAdapter()
    private var isReceiver = false
    private var resourceId: String? = null
    private var hostAddress: String? = null

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
                    val manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
                    val channel = manager.initialize(context, mainLooper, null)
                    manager.requestPeers(channel) { peerList ->
                        peers.clear()
                        peers.addAll(peerList.deviceList)
                        peerAdapter.notifyDataSetChanged()
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        val manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
                        val channel = manager.initialize(context, mainLooper, null)
                        manager.requestConnectionInfo(channel) { info ->
                            hostAddress = info.groupOwnerAddress.hostAddress
                            if (info.groupFormed && !info.isGroupOwner && !isReceiver) {
                                // We are the client, and we want to send
                                startSendProcess()
                            } else if (info.groupFormed && info.isGroupOwner && isReceiver) {
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
        
        lifecycleScope.launch {
            val realm = Realm.getDefaultInstance()
            val library = realm.where(RealmMyLibrary::class.java).equalTo("id", rId).findFirst()
            if (library == null) {
                realm.close()
                return@launch
            }

            val metadata = library.serializeResource().toString()
            val file = FileUtils.getSDPathFromUrl(this@P2pTransferActivity, library.resourceLocalAddress ?: library.resourceRemoteAddress)
            
            if (!file.exists()) {
                Toast.makeText(this@P2pTransferActivity, getString(R.string.file_not_found_locally), Toast.LENGTH_SHORT).show()
                realm.close()
                return@launch
            }

            binding.tvStatus.text = getString(R.string.status_sending, library.title)
            binding.pbTransfer.visibility = View.VISIBLE
            
            val success = p2pManager.sendFile(host, file, metadata) { progress ->
                runOnUiThread { binding.pbTransfer.progress = progress }
            }

            binding.tvStatus.text = if (success) getString(R.string.status_sent_success) else getString(R.string.status_send_failed)
            realm.close()
        }
    }

    private fun startReceiveProcess() {
        binding.tvStatus.text = getString(R.string.status_waiting_receive)
        binding.pbTransfer.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val success = p2pManager.receiveFile({ metadata, tempFile ->
                importResource(metadata, tempFile)
            }) { progress ->
                runOnUiThread { binding.pbTransfer.progress = progress }
            }
            binding.tvStatus.text = if (success) getString(R.string.status_received_imported) else getString(R.string.status_receive_failed)
        }
    }

    private fun importResource(metadataJson: String, tempFile: File) {
        val realm = Realm.getDefaultInstance()
        realm.executeTransaction { r ->
            try {
                val json = Gson().fromJson(metadataJson, com.google.gson.JsonObject::class.java)
                val library = r.createObject(RealmMyLibrary::class.java, java.util.UUID.randomUUID().toString())
                library._id = json.get("_id")?.asString
                library.title = json.get("title")?.asString
                library.description = json.get("description")?.asString
                library.resourceOffline = true
                
                val filename = json.get("filename")?.asString ?: tempFile.name
                val finalFile = File(FileUtils.getOlePath(this), "${library._id}/$filename")
                finalFile.parentFile?.mkdirs()
                tempFile.renameTo(finalFile)
                
                library.resourceLocalAddress = finalFile.absolutePath
                Toast.makeText(this, getString(R.string.imported_resource, library.title), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        realm.close()
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
