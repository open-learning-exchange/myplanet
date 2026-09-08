package org.ole.planet.myplanet.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import dagger.hilt.android.EntryPointAccessors
import java.util.Locale
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.di.CoreDependenciesEntryPoint
import org.ole.planet.myplanet.services.SharedPrefManager

object NetworkUtils {
    private class ResettableCache<T : Any>(private val initializer: () -> T) : ReadOnlyProperty<Any?, T> {
        @Volatile
        private var cached: T? = null

        override fun getValue(thisRef: Any?, property: KProperty<*>): T =
            cached ?: synchronized(this) { cached ?: initializer().also { cached = it } }

        fun reset() {
            synchronized(this) { cached = null }
        }
    }

    private val coreEntryPointCache = ResettableCache {
        EntryPointAccessors.fromApplication(context, CoreDependenciesEntryPoint::class.java)
    }

    private val coreEntryPoint: CoreDependenciesEntryPoint by coreEntryPointCache

    private val sharedPrefManagerCache = ResettableCache { coreEntryPoint.sharedPrefManager() }

    private val sharedPrefManager: SharedPrefManager by sharedPrefManagerCache

    private val coroutineScopeCache = ResettableCache { coreEntryPoint.applicationScope() }

    private val coroutineScope: CoroutineScope by coroutineScopeCache

    private val connectivityManagerCache = ResettableCache {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val connectivityManager: ConnectivityManager by connectivityManagerCache

    private val wifiManagerCache = ResettableCache {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private val wifiManager: WifiManager by wifiManagerCache

    private val bluetoothManagerCache = ResettableCache {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private val bluetoothManager: BluetoothManager by bluetoothManagerCache

    private val uniqueIdentifierCache = ResettableCache {
        val androidId = VersionUtils.getAndroidId(context)
        val buildId = Build.ID
        androidId + "_" + buildId
    }

    private val cachedUniqueIdentifier: String by uniqueIdentifierCache

    private val deviceNameCache = ResettableCache {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        if (model.startsWith(manufacturer)) {
            model.uppercase(Locale.ROOT)
        } else {
            "$manufacturer $model".uppercase(Locale.ROOT)
        }
    }

    private val cachedDeviceName: String by deviceNameCache

    private val _currentNetwork = MutableStateFlow(provideDefaultCurrentNetwork())

    private val isNetworkConnectedFlowCache = ResettableCache {
        _currentNetwork
            .map { it.isConnected() }
            .stateIn(scope = coroutineScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = _currentNetwork.value.isConnected())
    }

    val isNetworkConnectedFlow: StateFlow<Boolean> by isNetworkConnectedFlowCache

    private val resettableCaches = listOf(
        coreEntryPointCache,
        sharedPrefManagerCache,
        coroutineScopeCache,
        connectivityManagerCache,
        wifiManagerCache,
        bluetoothManagerCache,
        isNetworkConnectedFlowCache,
        uniqueIdentifierCache,
        deviceNameCache,
    )

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

    fun stopListenNetworkState() {
        if (!_currentNetwork.value.isListening) {
            return
        }

        connectivityManager.unregisterNetworkCallback(networkCallback)
        _currentNetwork.update { provideDefaultCurrentNetwork() }
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        if (_currentNetwork.value.isListening) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: IllegalArgumentException) {
            }
        }
        _currentNetwork.value = provideDefaultCurrentNetwork()
        resettableCaches.forEach { it.reset() }
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
        val adapter: BluetoothAdapter? = bluetoothManager.adapter
        return adapter != null && adapter.isEnabled
    }

    fun getCurrentNetworkId(context: Context): Int {
        var networkId = -1
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connManager.activeNetwork
        val capabilities = connManager.getNetworkCapabilities(network)
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val connectionInfo: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                capabilities.transportInfo as? WifiInfo
            } else {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
                @Suppress("DEPRECATION")
                wifiManager?.connectionInfo
            }

            if (connectionInfo != null && !connectionInfo.ssid.isNullOrEmpty()) {
                @Suppress("DEPRECATION")
                networkId = connectionInfo.networkId
            }
        }
        return networkId
    }

    fun getUniqueIdentifier(): String {
        return cachedUniqueIdentifier
    }

    fun getDeviceName(): String {
        return cachedDeviceName
    }

    fun getCustomDeviceName(context: Context): String {
        return sharedPrefManager.getCustomDeviceName()
    }

    fun extractProtocol(url: String): String? {
        val uri = url.trim().toUri()
        val scheme = uri.scheme
        return if (scheme != null && !scheme.contains(" ")) "$scheme://" else null
    }
}
