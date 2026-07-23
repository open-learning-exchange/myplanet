package org.ole.planet.myplanet.utils

import android.app.Application
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
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

    private lateinit var activity: ComponentActivity
    private lateinit var mockRootView: View

    @Before
    fun setup() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        mockRootView = mockk(relaxed = true)

        every { mockRootView.context } returns activity

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

        verify { ViewCompat.setOnApplyWindowInsetsListener(mockRootView, any()) }
    }
}
