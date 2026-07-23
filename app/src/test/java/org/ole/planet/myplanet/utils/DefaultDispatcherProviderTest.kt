package org.ole.planet.myplanet.utils

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultDispatcherProviderTest {

    private val dispatcherProvider = DefaultDispatcherProvider()

    @Test
    fun `test main returns Dispatchers Main`() {
        assertEquals(Dispatchers.Main, dispatcherProvider.main)
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
