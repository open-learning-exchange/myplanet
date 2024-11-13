package org.ole.planet.myplanet.utilities

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import java.util.Locale

object NetworkUtils {
    lateinit var coroutineScope: CoroutineScope

    fun initialize(coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope
    }

    private val connectivityManager: ConnectivityManager by lazy {
        MainApplication.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val _currentNetwork = MutableStateFlow(provideDefaultCurrentNetwork())

    val isNetworkConnectedFlow: StateFlow<Boolean> by lazy {
        _currentNetwork
            .map { it.isConnected() }
            .stateIn(scope = coroutineScope, started = SharingStarted.WhileSubscribed(), initialValue = _currentNetwork.value.isConnected())
    }

    val isNetworkConnected: Boolean
        get() = isNetworkConnectedFlow.value

    private val networkCallback = NetworkCallback()

    fun startListenNetworkState() {
        if (_currentNetwork.value.isListening) {
            return
        }

        _currentNetwork.update {
            provideDefaultCurrentNetwork().copy(isListening = true)
        }

        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    private class NetworkCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _currentNetwork.update {
                it.copy(isAvailable = true)
            }
        }

        override fun onLost(network: Network) {
            _currentNetwork.update {
                it.copy(isAvailable = false, networkCapabilities = null)
            }
        }

        override fun onUnavailable() {
            _currentNetwork.update {
                it.copy(isAvailable = false, networkCapabilities = null)
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _currentNetwork.update {
                it.copy(networkCapabilities = networkCapabilities)
            }
        }

        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            _currentNetwork.update {
                it.copy(isBlocked = blocked)
            }
        }
    }

    private fun provideDefaultCurrentNetwork(): CurrentNetwork {
        return CurrentNetwork(isListening = false, networkCapabilities = null, isAvailable = false, isBlocked = false)
    }

    private data class CurrentNetwork(val isListening: Boolean, val networkCapabilities: NetworkCapabilities?, val isAvailable: Boolean, val isBlocked: Boolean)

    private fun CurrentNetwork.isConnected(): Boolean {
        return isListening && isAvailable && !isBlocked && networkCapabilities.isNetworkCapabilitiesValid()
    }

    private fun NetworkCapabilities?.isNetworkCapabilitiesValid(): Boolean = when {
        this == null -> false
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && (hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || hasTransport(NetworkCapabilities.TRANSPORT_VPN) || hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) -> true
        else -> false
    }

    fun isWifiEnabled(): Boolean {
        val wifiManager = MainApplication.context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    fun isWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun isWifiBluetoothEnabled(): Boolean {
        return isBluetoothEnabled() || isWifiEnabled()
    }

    fun isBluetoothEnabled(): Boolean {
        val mBluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled
    }

    fun getCurrentNetworkId(context: Context): Int {
        var ssid = -1
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connManager.activeNetwork
        val capabilities = connManager.getNetworkCapabilities(network)
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
            val connectionInfo: WifiInfo? = wifiManager?.connectionInfo
            if (connectionInfo != null && !TextUtils.isEmpty(connectionInfo.ssid)) {
                ssid = connectionInfo.networkId
            }
        }
        return ssid
    }

    fun getUniqueIdentifier(): String {
        val androidId = Settings.Secure.getString(MainApplication.context.contentResolver, Settings.Secure.ANDROID_ID)
        val buildId = Build.ID
        return androidId + "_" + buildId
    }

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer)) {
            model.uppercase(Locale.ROOT)
        } else {
            "$manufacturer $model".uppercase(Locale.ROOT)
        }
    }

    fun getCustomDeviceName(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("customDeviceName", "") ?: ""
    }

    fun extractProtocol(url: String): String? {
        val uri = Uri.parse(url)
        val scheme = uri.scheme
        return if (scheme != null) "$scheme://" else null
    }
}
