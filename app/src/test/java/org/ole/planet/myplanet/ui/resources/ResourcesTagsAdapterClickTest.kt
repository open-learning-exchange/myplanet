package org.ole.planet.myplanet.ui.resources

import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.CheckBox
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnTagClickListener
import org.ole.planet.myplanet.model.TagData
import org.ole.planet.myplanet.model.TagEntity
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ResourcesTagsAdapterClickTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme_MaterialComponents)
    }

    private fun <T> submitListAndSync(adapter: ListAdapter<T, *>, list: List<T>) {
        val latch = CountDownLatch(1)
        adapter.submitList(list) {
            latch.countDown()
        }
        ShadowLooper.idleMainLooper()
        latch.await(2, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun testRebindChildRow_doesNotTriggerCheckboxListener_andToggleCallsListenerWithReboundTag() {
        val listener = mockk<OnTagClickListener>(relaxed = true)
        val adapter = ResourcesTagsAdapter(listener)

        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.itemAnimator = null
        recyclerView.adapter = adapter

        val tag1 = TagEntity().apply {
            id = "tag1"
            name = "Tag 1"
        }
        val child1 = TagData.Child(
            tag = tag1,
            isSelected = false,
            isSelectMultiple = true
        )

        submitListAndSync(adapter, listOf(child1))
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 100, 100)

        // Rebind with isSelected flipped and new tag name
        val tag2 = TagEntity().apply {
            id = "tag1"
            name = "Tag 1 Updated"
        }
        val child2 = TagData.Child(
            tag = tag2,
            isSelected = true,
            isSelectMultiple = true
        )

        submitListAndSync(adapter, listOf(child2))
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 100, 100)

        // Binding and rebinding should NOT call onCheckboxTagSelected
        verify(exactly = 0) { listener.onCheckboxTagSelected(any()) }

        // Toggling the checkbox now should call onCheckboxTagSelected once with the rebound tag
        val holder = recyclerView.findViewHolderForAdapterPosition(0)!!
        val checkBox = holder.itemView.findViewById<CheckBox>(R.id.checkbox)
        checkBox.performClick()

        verify(exactly = 1) { listener.onCheckboxTagSelected(match { it.name == "Tag 1 Updated" }) }
    }
}
