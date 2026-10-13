package org.ole.planet.myplanet.utils

import android.app.Application
import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.services.DownloadService
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DialogUtilsUpdateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `startDownloadUpdate starts DownloadService when checksum check returns false`() = runTest {
        val testPath = "/downloads/myplanet.apk"
        var lambdaCalledWithPath: String? = null

        DialogUtils.startDownloadUpdate(
            context = context,
            path = testPath,
            progressDialog = null,
            scope = this,
            checkCheckSum = { path ->
                lambdaCalledWithPath = path
                false
            }
        )
        advanceUntilIdle()

        assertEquals(testPath, lambdaCalledWithPath)

        val nextStartedService = Shadows.shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
        assertTrue(nextStartedService != null)
        assertEquals(DownloadService::class.java.name, nextStartedService?.component?.className)
    }

    @Test
    fun `startDownloadUpdate does not start DownloadService when checksum check returns true`() = runTest {
        val testPath = "/downloads/myplanet.apk"
        var lambdaCalledWithPath: String? = null

        DialogUtils.startDownloadUpdate(
            context = context,
            path = testPath,
            progressDialog = null,
            scope = this,
            checkCheckSum = { path ->
                lambdaCalledWithPath = path
                true
            }
        )
        advanceUntilIdle()

        assertEquals(testPath, lambdaCalledWithPath)

        val nextStartedService = Shadows.shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
        assertEquals(null, nextStartedService)
    }
}
