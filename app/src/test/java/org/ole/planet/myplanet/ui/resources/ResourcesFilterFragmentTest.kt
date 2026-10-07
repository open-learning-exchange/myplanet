package org.ole.planet.myplanet.ui.resources

import android.app.Application
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnFilterListener
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ResourcesFilterFragmentTest {

    @Test
    fun `test fragment inflates and iv_close is present`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)

        val fragment = ResourcesFilterFragment()
        fragment.show(activity.supportFragmentManager, "filter_dialog")
        activity.supportFragmentManager.executePendingTransactions()

        val view = fragment.view
        assertNotNull(view)
        val ivClose = view?.findViewById<ImageView>(R.id.iv_close)
        assertNotNull(ivClose)
    }


    @Test
    fun `test medium display name mapping`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)

        assertEquals("PDFs", ResourcesMediaType.displayName(activity, "pdf"))
        assertEquals("Videos", ResourcesMediaType.displayName(activity, "video"))
        assertEquals("Audio", ResourcesMediaType.displayName(activity, "audio"))
        assertEquals("Images", ResourcesMediaType.displayName(activity, "image"))
        assertEquals("CustomMedium", ResourcesMediaType.displayName(activity, "CustomMedium"))
    }

    @Test
    fun `test header labels update with formatted string resource when items selected`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)

        val fragment = ResourcesFilterFragment()
        fragment.setListener(object : OnFilterListener {
            override fun filter(subjects: MutableSet<String>, languages: MutableSet<String>, mediums: MutableSet<String>, levels: MutableSet<String>): Int = 0
            override fun getFilteredCount(subjects: Set<String>, languages: Set<String>, mediums: Set<String>, levels: Set<String>): Int = 0
            override suspend fun getData(): Map<String, Set<String>> = mapOf(
                "subjects" to setOf("Math", "Science"),
                "languages" to setOf("English"),
                "mediums" to setOf("pdf"),
                "levels" to setOf("Primary")
            )
            override fun getSelectedFilter(): Map<String, Set<String>> = mapOf(
                "subjects" to setOf("Math", "Science"),
                "languages" to setOf("English"),
                "mediums" to emptySet(),
                "levels" to emptySet()
            )
            override fun clearAllFilters() {}
        })

        fragment.show(activity.supportFragmentManager, "filter_dialog")
        activity.supportFragmentManager.executePendingTransactions()

        val view = fragment.view
        assertNotNull(view)
        val subjectsHeader = view?.findViewById<TextView>(R.id.subjects_layout)
        val languagesHeader = view?.findViewById<TextView>(R.id.languages_layout)
        val mediumsHeader = view?.findViewById<TextView>(R.id.mediums_layout)

        assertEquals("Subjects (2)", subjectsHeader?.text?.toString())
        assertEquals("Languages (1)", languagesHeader?.text?.toString())
        assertEquals("Mediums", mediumsHeader?.text?.toString())
    }
}
