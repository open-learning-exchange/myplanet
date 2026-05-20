package org.ole.planet.myplanet.utils

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class MyService @Inject constructor(private val dispatcherProvider: DispatcherProvider) {
    suspend fun doWork(): String = withContext(dispatcherProvider.io) {
        "work done"
    }
}

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class DispatcherProviderIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var myService: MyService

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun testServiceUsesDispatcher() = runTest {
        val result = myService.doWork()
        assertEquals("work done", result)
    }
}
