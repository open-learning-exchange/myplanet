package org.ole.planet.myplanet.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import dagger.hilt.android.EntryPointAccessors
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.ole.planet.myplanet.services.SharedPrefManager
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.di.CoreDependenciesEntryPoint

class NetworkUtilsMockTest {
    private lateinit var mockContext: Context
    private lateinit var mockWifiManager: WifiManager
    private lateinit var mockBluetoothManager: BluetoothManager
    private lateinit var mockBluetoothAdapter: BluetoothAdapter
    private lateinit var mockConnectivityManager: ConnectivityManager

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockWifiManager = mockk(relaxed = true)
        mockBluetoothManager = mockk(relaxed = true)
        mockBluetoothAdapter = mockk(relaxed = true)
        mockConnectivityManager = mockk(relaxed = true)

        mockkObject(MainApplication.Companion)
        every { MainApplication.context } returns mockContext

        mockkStatic(EntryPointAccessors::class)
        val mockEntryPoint = mockk<CoreDependenciesEntryPoint>(relaxed = true)
        every { EntryPointAccessors.fromApplication(any(), CoreDependenciesEntryPoint::class.java) } returns mockEntryPoint

        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getSystemService(Context.WIFI_SERVICE) } returns mockWifiManager
        every { mockContext.getSystemService(Context.BLUETOOTH_SERVICE) } returns mockBluetoothManager
        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager
        every { mockBluetoothManager.adapter } returns mockBluetoothAdapter
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `isWifiEnabled returns true when wifi is enabled`() {
        every { mockWifiManager.isWifiEnabled } returns true
        assertTrue(NetworkUtils.isWifiEnabled())
    }

    @Test
    fun `isWifiEnabled returns false when wifi is disabled`() {
        every { mockWifiManager.isWifiEnabled } returns false
        assertFalse(NetworkUtils.isWifiEnabled())
    }

    @Test
    fun `isBluetoothEnabled returns true when bluetooth is enabled`() {
        every { mockBluetoothAdapter.isEnabled } returns true
        assertTrue(NetworkUtils.isBluetoothEnabled())
    }

    @Test
    fun `isBluetoothEnabled returns false when bluetooth is disabled`() {
        every { mockBluetoothAdapter.isEnabled } returns false
        assertFalse(NetworkUtils.isBluetoothEnabled())
    }

    @Test
    fun `isBluetoothEnabled returns false when bluetooth adapter is null`() {
        every { mockBluetoothManager.adapter } returns null
        assertFalse(NetworkUtils.isBluetoothEnabled())
    }

    @Test
    fun `isWifiBluetoothEnabled returns true when wifi is enabled`() {
        every { mockWifiManager.isWifiEnabled } returns true
        every { mockBluetoothAdapter.isEnabled } returns false
        assertTrue(NetworkUtils.isWifiBluetoothEnabled())
    }

    @Test
    fun `isWifiBluetoothEnabled returns true when bluetooth is enabled`() {
        every { mockWifiManager.isWifiEnabled } returns false
        every { mockBluetoothAdapter.isEnabled } returns true
        assertTrue(NetworkUtils.isWifiBluetoothEnabled())
    }

    @Test
    fun `isWifiBluetoothEnabled returns false when both are disabled`() {
        every { mockWifiManager.isWifiEnabled } returns false
        every { mockBluetoothAdapter.isEnabled } returns false
        assertFalse(NetworkUtils.isWifiBluetoothEnabled())
    }

    @Test
    fun `getCustomDeviceName returns correct name from SharedPrefManager`() {
        val mockEntryPoint = mockk<CoreDependenciesEntryPoint>(relaxed = true)
        val mockSharedPrefManager = mockk<SharedPrefManager>()
        every { mockSharedPrefManager.getCustomDeviceName() } returns "Test Device Name"
        every { mockEntryPoint.sharedPrefManager() } returns mockSharedPrefManager
        every { EntryPointAccessors.fromApplication(any(), CoreDependenciesEntryPoint::class.java) } returns mockEntryPoint

        val result = NetworkUtils.getCustomDeviceName(mockContext)

        assertEquals("Test Device Name", result)
    }
}
