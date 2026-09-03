package org.ole.planet.myplanet.utils

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(application = Application::class)
class DefaultDispatcherProviderTest {

    private val dispatcherProvider = DefaultDispatcherProvider()

    @Test
    fun `test main returns Dispatchers Main`() {
        assertEquals(Dispatchers.Main, dispatcherProvider.main)
    }

    @Test
    fun `test mainImmediate returns Dispatchers Main immediate`() {
        assertEquals(Dispatchers.Main.immediate, dispatcherProvider.mainImmediate)
    }

    @Test
    fun `test io returns Dispatchers IO`() {
        assertEquals(Dispatchers.IO, dispatcherProvider.io)
    }

    @Test
    fun `test default returns Dispatchers Default`() {
        assertEquals(Dispatchers.Default, dispatcherProvider.default)
    }

    @Test
    fun `test unconfined returns Dispatchers Unconfined`() {
        assertEquals(Dispatchers.Unconfined, dispatcherProvider.unconfined)
    }
}
