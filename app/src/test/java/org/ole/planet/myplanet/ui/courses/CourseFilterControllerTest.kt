package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.TagEntity
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CourseFilterControllerTest {

    private lateinit var rootView: View

    @Before
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val themedContext = ContextThemeWrapper(baseContext, com.google.android.material.R.style.Theme_MaterialComponents)
        rootView = LayoutInflater.from(themedContext).inflate(R.layout.fragment_my_course, null, false)
    }

    @Test
    fun testProgressFilterIsClearedOnClearAll() = runTest {
        var scrolledToTop = false
        val controller = CourseFilterController(
            rootView = rootView,
            coroutineScope = backgroundScope,
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
        assertTrue(scrolledToTop)
    }

    @Test
    fun testClearAllResetsAllFilters() = runTest {
        var scrolledToTop = false
        val controller = CourseFilterController(
            rootView = rootView,
            coroutineScope = backgroundScope,
            onScrollToTop = { scrolledToTop = true }
        )
        controller.setup()

        val etSearch = rootView.findViewById<EditText>(R.id.et_search)

        etSearch.setText("Algebra")
        controller.setGradeSubject("Grade 1", "Math")
        controller.setProgressFilter("Completed")
        controller.addTag(TagEntity().apply { name = "Math" })

        assertTrue(controller.filterApplied())
        assertEquals("Completed", controller.currentState().progressFilter)
        assertEquals("Grade 1", controller.currentGrade())
        assertEquals("Math", controller.currentSubject())

        controller.clearAll()

        assertEquals("", etSearch.text.toString())
        assertEquals("", controller.currentGrade())
        assertEquals("", controller.currentSubject())
        assertEquals("", controller.currentState().progressFilter)
        assertEquals("", controller.filterState.value.progressFilter)
        assertTrue(controller.searchTags.isEmpty())
        assertFalse(controller.filterApplied())
        assertTrue(scrolledToTop)
    }
}
