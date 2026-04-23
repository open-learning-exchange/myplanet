package org.ole.planet.myplanet.base

import io.realm.RealmList
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.RealmMyLibrary
import androidx.recyclerview.widget.RecyclerView

class BaseRecyclerFragmentTest {

    // Create a dummy concrete class to test BaseRecyclerFragment
    class TestFragment : BaseRecyclerFragment<Any>() {
        override fun getLayout(): Int = 0

        override suspend fun getAdapter(): RecyclerView.Adapter<out RecyclerView.ViewHolder> {
            return object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    TODO("Not yet implemented")
                }
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
                override fun getItemCount(): Int = 0
            }
        }
    }

    @Test
    fun testApplyFilter() {
        val fragment = TestFragment()

        val lib1 = RealmMyLibrary().apply {
            subject = RealmList("Math")
            level = RealmList("Beginner")
            language = "English"
            mediaType = "Video"
        }

        val lib2 = RealmMyLibrary().apply {
            subject = RealmList("Science")
            level = RealmList("Advanced")
            language = "Spanish"
            mediaType = "PDF"
        }

        val libraries = listOf(lib1, lib2)

        // No filters
        assertEquals(2, fragment.applyFilter(libraries).size)

        // Subject filter
        fragment.subjects = mutableSetOf("Math")
        assertEquals(1, fragment.applyFilter(libraries).size)
        assertEquals(lib1, fragment.applyFilter(libraries)[0])

        // Reset and apply Language filter
        fragment.subjects = mutableSetOf()
        fragment.languages = mutableSetOf("Spanish")
        assertEquals(1, fragment.applyFilter(libraries).size)
        assertEquals(lib2, fragment.applyFilter(libraries)[0])

        // Multiple filters matching one
        fragment.languages = mutableSetOf("English")
        fragment.mediums = mutableSetOf("Video")
        assertEquals(1, fragment.applyFilter(libraries).size)
        assertEquals(lib1, fragment.applyFilter(libraries)[0])

        // Multiple filters matching none
        fragment.languages = mutableSetOf("English")
        fragment.mediums = mutableSetOf("PDF")
        assertEquals(0, fragment.applyFilter(libraries).size)
    }
}
