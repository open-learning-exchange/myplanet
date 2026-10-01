package org.ole.planet.myplanet.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidNetworkStatusTest {

    private val network: Network = mockk()
    private val connectivityManager: ConnectivityManager = mockk {
        every { activeNetwork } returns network
    }
    private val context: Context = mockk {
        every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
    }
    private val networkStatus = AndroidNetworkStatus(context)

    @Test
    fun `currentWifiSsid is null when there is no active network capability`() {
        every { connectivityManager.getNetworkCapabilities(network) } returns null

        assertNull(networkStatus.currentWifiSsid())
    }

    @Test
    fun `currentWifiSsid is null when the active network is not wifi`() {
        val capabilities: NetworkCapabilities = mockk {
            every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        }
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities

        assertNull(networkStatus.currentWifiSsid())
    }
}
