package org.ole.planet.myplanet.di

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class RealmDispatcherInjectionTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var realmDispatcherProvider: LegacyRealmDispatcherProvider

    @Inject
    @LegacyRealmDispatcher
    lateinit var injectedDispatcher: CoroutineDispatcher

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun testLegacyRealmDispatcherProviderInjection() {
        assertNotNull(realmDispatcherProvider)
        assertNotNull(injectedDispatcher)

        // Injected dispatcher is exactly the provider instance
        assertEquals(realmDispatcherProvider, injectedDispatcher)

        realmDispatcherProvider.shutdown()
    }
}
