package org.ole.planet.myplanet.utils

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class EdgeToEdgeUtilsTest {

    private lateinit var activity: Activity
    private lateinit var mockRootView: View
    private lateinit var mockInsetsController: WindowInsetsControllerCompat

    @Before
    fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        mockRootView = mockk(relaxed = true)
        mockInsetsController = mockk(relaxed = true)

        mockkStatic(WindowCompat::class)
        every { WindowCompat.setDecorFitsSystemWindows(any(), any()) } returns Unit
        every { WindowCompat.getInsetsController(any(), any()) } returns mockInsetsController

        mockkStatic(ViewCompat::class)
        every { ViewCompat.setOnApplyWindowInsetsListener(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `setupEdgeToEdge should configure window and insets`() {
        EdgeToEdgeUtils.setupEdgeToEdge(
            activity = activity,
            rootView = mockRootView,
            lightStatusBar = true,
            lightNavigationBar = false
        )

        verify {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowCompat.getInsetsController(activity.window, mockRootView)
        }
        verify { mockInsetsController.isAppearanceLightStatusBars = true }
        verify { mockInsetsController.isAppearanceLightNavigationBars = false }
        verify { ViewCompat.setOnApplyWindowInsetsListener(mockRootView, any()) }
    }

    @Test
    fun `setupEdgeToEdgeWithKeyboard should configure window and insets`() {
        EdgeToEdgeUtils.setupEdgeToEdgeWithKeyboard(
            activity = activity,
            rootView = mockRootView,
            lightStatusBar = false,
            lightNavigationBar = true
        )

        verify {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowCompat.getInsetsController(activity.window, mockRootView)
        }
        verify { mockInsetsController.isAppearanceLightStatusBars = false }
        verify { mockInsetsController.isAppearanceLightNavigationBars = true }
        verify { ViewCompat.setOnApplyWindowInsetsListener(mockRootView, any()) }
    }

    @Test
    fun `setupEdgeToEdge in light mode should default to light status bar and light navigation bar`() {
        val configuration = activity.resources.configuration
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO

        EdgeToEdgeUtils.setupEdgeToEdge(
            activity = activity,
            rootView = mockRootView
        )

        verify { mockInsetsController.isAppearanceLightStatusBars = true }
        verify { mockInsetsController.isAppearanceLightNavigationBars = true }
    }

    @Test
    fun `setupEdgeToEdge in dark mode should default to dark status bar and dark navigation bar`() {
        val configuration = activity.resources.configuration
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES

        EdgeToEdgeUtils.setupEdgeToEdge(
            activity = activity,
            rootView = mockRootView
        )

        verify { mockInsetsController.isAppearanceLightStatusBars = false }
        verify { mockInsetsController.isAppearanceLightNavigationBars = false }
    }

    @Test
    fun `setupEdgeToEdgeWithKeyboard in dark mode should default to dark status bar and dark navigation bar`() {
        val configuration = activity.resources.configuration
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES

        EdgeToEdgeUtils.setupEdgeToEdgeWithKeyboard(
            activity = activity,
            rootView = mockRootView
        )

        verify { mockInsetsController.isAppearanceLightStatusBars = false }
        verify { mockInsetsController.isAppearanceLightNavigationBars = false }
    }
}
