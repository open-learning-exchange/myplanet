package org.ole.planet.myplanet.utils

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.graphics.Insets
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EdgeToEdgeUtilsTest {

    private lateinit var mockActivity: Activity
    private lateinit var mockWindow: Window
    private lateinit var mockRootView: View
    private lateinit var mockInsetsController: WindowInsetsControllerCompat
    private lateinit var mockResources: Resources

    @Before
    fun setup() {
        mockActivity = mockk()
        mockWindow = mockk(relaxed = true)
        mockRootView = mockk(relaxed = true)
        mockInsetsController = mockk(relaxed = true)
        mockResources = mockk()

        every { mockActivity.window } returns mockWindow
        every { mockResources.configuration } returns Configuration().apply {
            uiMode = Configuration.UI_MODE_NIGHT_NO
        }
        every { mockActivity.resources } returns mockResources

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
            activity = mockActivity,
            rootView = mockRootView,
            lightStatusBar = true,
            lightNavigationBar = false
        )

        verify {
            WindowCompat.setDecorFitsSystemWindows(mockWindow, false)
            WindowCompat.getInsetsController(mockWindow, mockRootView)
        }
        verify { mockInsetsController.isAppearanceLightStatusBars = true }
        verify { mockInsetsController.isAppearanceLightNavigationBars = false }
        verify { ViewCompat.setOnApplyWindowInsetsListener(mockRootView, any()) }
    }

    @Test
    fun `setupEdgeToEdgeWithKeyboard should configure window and insets`() {
        EdgeToEdgeUtils.setupEdgeToEdgeWithKeyboard(
            activity = mockActivity,
            rootView = mockRootView,
            lightStatusBar = false,
            lightNavigationBar = true
        )

        verify {
            WindowCompat.setDecorFitsSystemWindows(mockWindow, false)
            WindowCompat.getInsetsController(mockWindow, mockRootView)
        }
        verify { mockInsetsController.isAppearanceLightStatusBars = false }
        verify { mockInsetsController.isAppearanceLightNavigationBars = true }
        verify { ViewCompat.setOnApplyWindowInsetsListener(mockRootView, any()) }
    }

    @Test
    fun `setupEdgeToEdgeWithStatusBarToolbar pins toolbar height so status bar padding is not clipped away`() {
        val toolbarBaseHeight = 140
        val toolbarLayoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val toolbar = mockk<View>(relaxed = true)
        every { toolbar.paddingTop } returns 0
        every { toolbar.paddingLeft } returns 0
        every { toolbar.paddingRight } returns 0
        every { toolbar.paddingBottom } returns 0
        every { toolbar.minimumHeight } returns toolbarBaseHeight
        every { toolbar.layoutParams } returns toolbarLayoutParams

        val listenerSlot = slot<OnApplyWindowInsetsListener>()
        every { ViewCompat.setOnApplyWindowInsetsListener(mockRootView, capture(listenerSlot)) } returns Unit

        EdgeToEdgeUtils.setupEdgeToEdgeWithStatusBarToolbar(
            activity = mockActivity,
            rootView = mockRootView,
            toolbar = toolbar
        )

        val statusBarInset = 152
        val navBarInset = 59
        val windowInsets = mockk<WindowInsetsCompat>()
        every { windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()) } returns
            Insets.of(0, statusBarInset, 0, navBarInset)

        listenerSlot.captured.onApplyWindowInsets(mockRootView, windowInsets)

        // The root view keeps 0 top padding so the toolbar's own background can extend
        // behind the status bar; the toolbar absorbs the inset as top padding instead.
        verify { mockRootView.setPadding(0, 0, 0, navBarInset) }
        verify { toolbar.setPadding(0, statusBarInset, 0, 0) }
        // A wrap_content toolbar does not grow on its own to fit the extra padding, so its
        // height must be pinned explicitly or the padded-down content renders below the
        // toolbar's visible bounds (i.e. is clipped away entirely).
        assertEquals(toolbarBaseHeight + statusBarInset, toolbarLayoutParams.height)
    }
}
