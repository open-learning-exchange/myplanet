package org.ole.planet.myplanet.utils

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject
import org.junit.Assert.assertNotNull

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class DispatcherProviderDITest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun testDispatcherProviderInjection() {
        assertNotNull(dispatcherProvider)
        assertNotNull(dispatcherProvider.main)
        assertNotNull(dispatcherProvider.io)
        assertNotNull(dispatcherProvider.default)
        assertNotNull(dispatcherProvider.unconfined)
    }
}
