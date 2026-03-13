package org.ole.planet.myplanet

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.ole.planet.myplanet.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

class MainApplicationTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testDispatcherProviderInjection() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val testDispatcherProvider = object : DispatcherProvider {
            override val main: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
            override val unconfined: CoroutineDispatcher = testDispatcher
        }

        val app = MainApplication()
        app.dispatcherProvider = testDispatcherProvider

        assertNotNull(app.dispatcherProvider)
        assertNotNull(app.dispatcherProvider.io)
    }
}
