package org.ole.planet.myplanet.ui.resources

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.widget.FrameLayout
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnFilterListener
import org.ole.planet.myplanet.model.TagEntity
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class FilterSheetHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id = R.id.fragment_container })
    }
}

class FilterSheetHostFragment : Fragment(), OnFilterListener {
    var cleared = 0
    override fun filter(subjects: MutableSet<String>, languages: MutableSet<String>, mediums: MutableSet<String>, levels: MutableSet<String>) = 0
    override fun getFilteredCount(subjects: Set<String>, languages: Set<String>, mediums: Set<String>, levels: Set<String>) = 0
    override suspend fun getData(): Map<String, Set<String>> = mapOf(
        "subjects" to setOf("Math", "Science"),
        "languages" to setOf("English"),
        "mediums" to setOf("pdf"),
        "levels" to setOf("Primary")
    )
    override fun getSelectedFilter(): Map<String, Set<String>> = emptyMap()
    override fun clearAllFilters() { cleared++ }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ResourcesSheetsRotationTest {

    @Test
    fun filterSheet_afterRotation_stillListsFiltersAndClears() {
        val controller = Robolectric.buildActivity(FilterSheetHostActivity::class.java).setup()
        controller.get().supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, FilterSheetHostFragment(), HOST).commitNow()
        val host = controller.get().supportFragmentManager.findFragmentByTag(HOST) as FilterSheetHostFragment
        ResourcesFilterFragment().apply { setListener(host) }.show(host.childFragmentManager, SHEET)
        host.childFragmentManager.executePendingTransactions()

        controller.recreate()
        shadowOf(Looper.getMainLooper()).idle()

        val newHost = controller.get().supportFragmentManager.findFragmentByTag(HOST) as FilterSheetHostFragment
        val sheet = newHost.childFragmentManager.findFragmentByTag(SHEET) as ResourcesFilterFragment
        val view = assertNotNull(sheet.view).let { sheet.requireView() }
        assertEquals(2, view.findViewById<ListView>(R.id.list_sub).adapter?.count)

        view.findViewById<android.view.View>(R.id.btn_clear_tags).performClick()
        assertEquals(1, newHost.cleared)
    }

    @Test
    fun collectionsSheet_keepsTheTicksMadeInsideItAcrossRotation() {
        val ticked = listOf(
            TagEntity().apply { id = "t1"; name = "Math" },
            TagEntity().apply { id = ""; name = "Unsynced" }
        )
        val state = Bundle()
        CollectionsFragment.writeSelection(state, ticked)

        val restored = CollectionsFragment.readSelection(state)!!

        assertEquals(listOf("t1", ""), restored.map { it.id })
        assertEquals(listOf("Math", "Unsynced"), restored.map { it.name })
        assertNull(CollectionsFragment.readSelection(Bundle()))
    }

    private companion object {
        const val HOST = "host"
        const val SHEET = "sheet"
    }
}
