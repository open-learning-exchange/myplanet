package org.ole.planet.myplanet.ui.courses

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.TagEntity
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CourseFilterControllerTest {

    @Test
    fun testProgressFilterIsClearedOnClearAll() {
        val baseContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val themedContext = ContextThemeWrapper(baseContext, com.google.android.material.R.style.Theme_MaterialComponents)
        val rootView = LayoutInflater.from(themedContext).inflate(R.layout.fragment_my_course, null, false)

        val testDispatcher = UnconfinedTestDispatcher()
        val testScope = TestScope(testDispatcher)

        var scrolledToTop = false
        val controller = CourseFilterController(
            rootView = rootView,
            coroutineScope = testScope,
            onScrollToTop = { scrolledToTop = true }
        )
        controller.setup()

        controller.setProgressFilter("In Progress")
        assertEquals("In Progress", controller.currentState().progressFilter)
        assertTrue(controller.filterApplied())
        assertEquals("In Progress", controller.filterState.value.progressFilter)

        controller.clearAll()
        assertEquals("", controller.currentState().progressFilter)
        assertEquals("", controller.filterState.value.progressFilter)
        assertFalse(controller.filterApplied())
    }

    @Test
    fun testClearButtonResetsAllFiltersAndHidesFilterCard() {
        val baseContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val themedContext = ContextThemeWrapper(baseContext, com.google.android.material.R.style.Theme_MaterialComponents)
        val rootView = LayoutInflater.from(themedContext).inflate(R.layout.fragment_my_course, null, false)

        val testDispatcher = UnconfinedTestDispatcher()
        val testScope = TestScope(testDispatcher)

        val controller = CourseFilterController(
            rootView = rootView,
            coroutineScope = testScope,
            onScrollToTop = {}
        )
        controller.setup()

        val cardFilter = rootView.findViewById<View>(R.id.card_filter)
        val btnClear = rootView.findViewById<Button>(R.id.btn_clear_tags)
        val etSearch = rootView.findViewById<EditText>(R.id.et_search)
        val spnGrade = rootView.findViewById<Spinner>(R.id.spn_grade)
        val spnSubject = rootView.findViewById<Spinner>(R.id.spn_subject)

        cardFilter.visibility = View.VISIBLE
        etSearch.setText("Algebra")
        spnGrade.setSelection(1)
        spnSubject.setSelection(1)
        controller.setProgressFilter("Completed")
        controller.addTag(TagEntity().apply { name = "Math" })

        assertTrue(controller.filterApplied())
        assertEquals("Completed", controller.currentState().progressFilter)

        btnClear.performClick()

        assertEquals(View.GONE, cardFilter.visibility)
        assertEquals("", etSearch.text.toString())
        assertEquals(0, spnGrade.selectedItemPosition)
        assertEquals(0, spnSubject.selectedItemPosition)
        assertEquals("", controller.currentState().progressFilter)
        assertTrue(controller.searchTags.isEmpty())
        assertFalse(controller.filterApplied())
    }
}
