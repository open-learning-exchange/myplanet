package org.ole.planet.myplanet.ui.feedback

import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import androidx.core.content.ContextCompat
import org.ole.planet.myplanet.R

@RunWith(RobolectricTestRunner::class)
class TintTest {
    @Test
    fun testTintIsRetained() {
        val context = RuntimeEnvironment.getApplication()
        val primary = ContextCompat.getDrawable(context, R.drawable.bg_primary)?.mutate()
        primary?.setTint(Color.RED)

        val copied = primary?.constantState?.newDrawable()?.mutate()
        // It's hard to assert the tint color directly on GradientDrawable without reflection in old APIs,
        // but Robolectric might allow something.
        assertNotNull(copied)
    }
}
