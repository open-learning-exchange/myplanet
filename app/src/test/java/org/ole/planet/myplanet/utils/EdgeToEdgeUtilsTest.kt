package org.ole.planet.myplanet.utils

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EdgeToEdgeUtilsTest {

    private lateinit var activity: AppCompatActivity
    private lateinit var root: FrameLayout

    @Before
    fun setUp() {
        // AppCompatDelegate's night mode is static; other tests (ThemeManagerTest) leave it set.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        root = FrameLayout(activity)
        activity.setContentView(root)
    }

    private fun insets(systemBars: Insets, cutout: Insets = Insets.NONE, ime: Insets = Insets.NONE) =
        WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), systemBars)
            .setInsets(WindowInsetsCompat.Type.displayCutout(), cutout)
            .setInsets(WindowInsetsCompat.Type.ime(), ime)
            .build()

    private fun controller(view: View = root) = WindowCompat.getInsetsController(activity.window, view)

    @Test
    fun `setupEdgeToEdge pads root by system bars and consumes insets`() {
        EdgeToEdgeUtils.setupEdgeToEdge(activity, root)

        val result = ViewCompat.dispatchApplyWindowInsets(root, insets(Insets.of(1, 2, 3, 4)))

        assertEquals(listOf(1, 2, 3, 4), listOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom))
        assertTrue(result.isConsumed)
    }

    @Test
    fun `setupEdgeToEdge keeps content out of a display cutout wider than the bars`() {
        EdgeToEdgeUtils.setupEdgeToEdge(activity, root)

        ViewCompat.dispatchApplyWindowInsets(root, insets(Insets.of(0, 20, 0, 10), cutout = Insets.of(30, 0, 0, 0)))

        assertEquals(30, root.paddingLeft)
        assertEquals(20, root.paddingTop)
    }

    @Test
    fun `setupEdgeToEdgeWithKeyboard pads bottom by the keyboard when it is taller`() {
        EdgeToEdgeUtils.setupEdgeToEdgeWithKeyboard(activity, root)

        ViewCompat.dispatchApplyWindowInsets(root, insets(Insets.of(0, 20, 0, 10), ime = Insets.of(0, 0, 0, 300)))
        assertEquals(300, root.paddingBottom)

        ViewCompat.dispatchApplyWindowInsets(root, insets(Insets.of(0, 20, 0, 10)))
        assertEquals(10, root.paddingBottom)
    }

    @Test
    fun `setupEdgeToEdgeWithKeyboard paints the status bar strip and still avoids the keyboard`() {
        root.setBackgroundColor(Color.WHITE)

        EdgeToEdgeUtils.setupEdgeToEdgeWithKeyboard(activity, root, statusBarColor = Color.BLUE)
        ViewCompat.dispatchApplyWindowInsets(root, insets(Insets.of(0, 24, 0, 10), ime = Insets.of(0, 0, 0, 300)))

        assertEquals(300, root.paddingBottom)
        assertEquals(24, (root.background as LayerDrawable).getLayerHeight(1))
    }

    @Test
    fun `setupEdgeToEdge ignores the keyboard`() {
        EdgeToEdgeUtils.setupEdgeToEdge(activity, root)

        ViewCompat.dispatchApplyWindowInsets(root, insets(Insets.of(0, 20, 0, 10), ime = Insets.of(0, 0, 0, 300)))

        assertEquals(10, root.paddingBottom)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `bar icons default to dark in light mode`() {
        EdgeToEdgeUtils.setupEdgeToEdge(activity, root)

        assertTrue(controller().isAppearanceLightStatusBars)
    }

    @Test
    @Config(qualifiers = "night")
    fun `bar icons default to light in night mode`() {
        EdgeToEdgeUtils.setupEdgeToEdge(activity, root)

        assertFalse(controller().isAppearanceLightStatusBars)
    }

    @Test
    fun `explicit bar appearance overrides the day night default`() {
        EdgeToEdgeUtils.setupEdgeToEdge(activity, root, lightStatusBar = false, lightNavigationBar = false)

        assertFalse(controller().isAppearanceLightStatusBars)
        assertFalse(controller().isAppearanceLightNavigationBars)
    }

    @Test
    fun `keepClearOfSystemBars adds insets to the inflated margins`() {
        val button = View(activity)
        root.addView(button, FrameLayout.LayoutParams(10, 10).apply { setMargins(16, 16, 16, 16) })

        EdgeToEdgeUtils.keepClearOfSystemBars(button)
        ViewCompat.dispatchApplyWindowInsets(button, insets(Insets.of(0, 24, 0, 0), cutout = Insets.of(0, 0, 40, 0)))
        // A second pass must not stack on the first.
        ViewCompat.dispatchApplyWindowInsets(button, insets(Insets.of(0, 24, 0, 0), cutout = Insets.of(0, 0, 40, 0)))

        val lp = button.layoutParams as FrameLayout.LayoutParams
        assertEquals(listOf(16, 40, 56, 16), listOf(lp.leftMargin, lp.topMargin, lp.rightMargin, lp.bottomMargin))
    }

    @Test
    fun `statusBarColor paints a strip as tall as the status bar over the root background`() {
        root.setBackgroundColor(Color.WHITE)

        EdgeToEdgeUtils.setupEdgeToEdge(activity, root, statusBarColor = Color.BLUE)
        ViewCompat.dispatchApplyWindowInsets(root, insets(Insets.of(0, 24, 0, 10)))

        val background = root.background as LayerDrawable
        assertEquals(Color.WHITE, (background.getDrawable(0) as ColorDrawable).color)
        assertEquals(Color.BLUE, (background.getDrawable(1) as ColorDrawable).color)
        assertEquals(24, background.getLayerHeight(1))
    }
}
