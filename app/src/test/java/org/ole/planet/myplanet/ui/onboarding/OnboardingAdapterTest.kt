package org.ole.planet.myplanet.ui.onboarding

import android.app.Application
import android.content.Context
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.OnboardingItem
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OnboardingAdapterTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
    }

    @Test
    fun `test instantiateItem sets text colors equal to daynight textColor`() {
        val item = OnboardingItem().apply {
            title = "Title 1"
            description = "Desc 1"
            imageID = R.drawable.ole_logo
        }
        val items = arrayListOf(item)
        val adapter = OnboardingAdapter(context, items)
        val container = FrameLayout(context)

        val itemView = adapter.instantiateItem(container, 0) as android.view.View
        val tvHeader = itemView.findViewById<TextView>(R.id.tv_header)
        val tvDesc = itemView.findViewById<TextView>(R.id.tv_desc)

        val expectedColor = context.getColor(R.color.daynight_textColor)
        assertEquals(expectedColor, tvHeader.currentTextColor)
        assertEquals(expectedColor, tvDesc.currentTextColor)
    }
}
