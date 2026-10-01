package org.ole.planet.myplanet.utils

/**
 * Injectable view of the device's network state, so sync code doesn't talk to the platform's
 * connectivity services directly. The Android implementation is [AndroidNetworkStatus].
 */
interface NetworkStatus {
    /**
     * SSID of the Wi-Fi network the active connection runs over, or null when the active
     * network isn't Wi-Fi or the SSID can't be read.
     */
    fun currentWifiSsid(): String?

    /**
     * Platform network id of the Wi-Fi network the active connection runs over, or -1 when the
     * active network isn't Wi-Fi or the id can't be read.
     */
    fun currentWifiNetworkId(): Int
}
