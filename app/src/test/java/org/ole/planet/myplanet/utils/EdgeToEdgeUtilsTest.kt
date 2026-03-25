package org.ole.planet.myplanet.utils

import android.app.Activity
import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class EdgeToEdgeUtilsTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    private lateinit var mockActivity: Activity
    private lateinit var mockWindow: Window
    private lateinit var mockRootView: View
    private lateinit var mockInsetsController: WindowInsetsControllerCompat

    @Before
    fun setup() {
        hiltRule.inject()
        mockActivity = mockk()
        mockWindow = mockk(relaxed = true)
        mockRootView = mockk(relaxed = true)
        mockInsetsController = mockk(relaxed = true)

        every { mockActivity.window } returns mockWindow

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
}
