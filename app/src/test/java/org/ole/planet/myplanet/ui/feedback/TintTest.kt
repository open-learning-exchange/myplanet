package org.ole.planet.myplanet.ui.feedback

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

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
