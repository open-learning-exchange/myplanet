package org.ole.planet.myplanet.base

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class BaseDashboardFragmentLayoutTest {

    private fun getThemedContext(): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        return ContextThemeWrapper(app, R.style.AppTheme_MaterialComponents)
    }

    @Test
    @Config(qualifiers = "sw600dp")
    fun tabletDashboardCardsUseScrollViewAndWrapFlexbox() {
        val context = getThemedContext()
        val inflater = LayoutInflater.from(context)

        val cardLayouts = listOf(
            R.layout.home_card_courses to R.id.flexboxLayoutCourse,
            R.layout.home_card_library to R.id.flexboxLayout,
            R.layout.home_card_teams to R.id.flexboxLayoutTeams,
            R.layout.home_card_mylife to R.id.flexboxLayoutMyLife
        )

        for ((layoutRes, flexboxId) in cardLayouts) {
            val card = inflater.inflate(layoutRes, null)
            val flexbox = card.findViewById<FlexboxLayout>(flexboxId)

            val scrollContainer = flexbox.parent
            assertTrue(
                "Card $layoutRes scroll container must be a vertical ScrollView",
                scrollContainer is ScrollView
            )
            assertFalse(
                "Card $layoutRes scroll container must NOT be a HorizontalScrollView",
                scrollContainer is HorizontalScrollView
            )
            assertEquals(
                "Flexbox in card $layoutRes must wrap items",
                FlexWrap.WRAP,
                flexbox.flexWrap
            )
        }
    }

    @Test
    @Config(qualifiers = "sw600dp")
    fun tabletDashboardCoursesCardWrapsChipsIntoMultipleRows() {
        val context = getThemedContext()
        val inflater = LayoutInflater.from(context)
        val card = inflater.inflate(R.layout.home_card_courses, null)
        val flexbox = card.findViewById<FlexboxLayout>(R.id.flexboxLayoutCourse)

        val chipWidth = context.resources.getDimensionPixelSize(R.dimen.dashboard_chip_width)
        val chipGap = context.resources.getDimensionPixelSize(R.dimen.dashboard_chip_gap)

        for (i in 1..8) {
            val child = inflater.inflate(R.layout.item_course_home, flexbox, false)
            val params = FlexboxLayout.LayoutParams(
                chipWidth,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                flexShrink = 0f
                marginEnd = chipGap
            }
            child.findViewById<TextView>(R.id.title).text = "Course $i"
            flexbox.addView(child, params)
        }

        // Measure card with 600px width and 400px height (tablet card dimensions)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        card.measure(widthSpec, heightSpec)
        card.layout(0, 0, 600, 400)

        // Verify chips wrapped across multiple rows
        val singleRowHeight = flexbox.getChildAt(0).measuredHeight
        assertTrue("Single row height should be positive", singleRowHeight > 0)
        assertTrue(
            "Flexbox measured height (${flexbox.measuredHeight}) should be greater than single row height ($singleRowHeight)",
            flexbox.measuredHeight > singleRowHeight * 2
        )
    }

    @Test
    @Config(qualifiers = "sw360dp")
    fun phoneDashboardCardsUseHorizontalScrollViewAndNowrap() {
        val context = getThemedContext()
        val inflater = LayoutInflater.from(context)

        val cardLayouts = listOf(
            R.layout.home_card_courses to R.id.flexboxLayoutCourse,
            R.layout.home_card_library to R.id.flexboxLayout,
            R.layout.home_card_teams to R.id.flexboxLayoutTeams,
            R.layout.home_card_mylife to R.id.flexboxLayoutMyLife
        )

        for ((layoutRes, flexboxId) in cardLayouts) {
            val card = inflater.inflate(layoutRes, null)
            val flexbox = card.findViewById<FlexboxLayout>(flexboxId)

            val scrollContainer = flexbox.parent
            assertTrue(
                "Phone card $layoutRes scroll container must be a HorizontalScrollView",
                scrollContainer is HorizontalScrollView
            )
            assertEquals(
                "Phone flexbox in card $layoutRes must not wrap items",
                FlexWrap.NOWRAP,
                flexbox.flexWrap
            )
        }
    }
}
