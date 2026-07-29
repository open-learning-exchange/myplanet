package org.ole.planet.myplanet.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
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
