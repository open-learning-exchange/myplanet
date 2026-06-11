package org.ole.planet.myplanet.utils

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.Shadows.shadowOf

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class NetworkUtilsStateTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun init() {
        hiltRule.inject()
        org.ole.planet.myplanet.MainApplication.context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun startListenNetworkState_isIdempotent() {
        val connectivityManager = ApplicationProvider.getApplicationContext<Context>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowConnectivityManager = shadowOf(connectivityManager)

        val initialSize = shadowConnectivityManager.networkCallbacks.size

        NetworkUtils.startListenNetworkState()
        assertEquals(initialSize + 1, shadowConnectivityManager.networkCallbacks.size)

        // Calling it again should not register duplicate callbacks
        NetworkUtils.startListenNetworkState()
        assertEquals(initialSize + 1, shadowConnectivityManager.networkCallbacks.size)

        NetworkUtils.stopListenNetworkState()
        assertEquals(initialSize, shadowConnectivityManager.networkCallbacks.size)
    }
}
