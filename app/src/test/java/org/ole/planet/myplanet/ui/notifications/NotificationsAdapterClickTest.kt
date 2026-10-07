package org.ole.planet.myplanet.ui.notifications

import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.Notification
import org.ole.planet.myplanet.model.NotificationListItem
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotificationsAdapterClickTest {

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
    fun testRebindSelectionModeAndNormalMode() {
        val onMarkAsReadClick = mockk<(String) -> Unit>(relaxed = true)
        val onNotificationClick = mockk<(Notification) -> Unit>(relaxed = true)
        val onToggleSelection = mockk<(String) -> Unit>(relaxed = true)
        val onToggleGroupExpansion = mockk<(String) -> Unit>(relaxed = true)

        val adapter = NotificationsAdapter(
            onMarkAsReadClick = onMarkAsReadClick,
            onNotificationClick = onNotificationClick,
            onToggleSelection = onToggleSelection,
            onToggleGroupExpansion = onToggleGroupExpansion,
            now = { 1_000_000L }
        )

        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.itemAnimator = null
        recyclerView.adapter = adapter

        val notif1 = Notification("1", "First Text", false, "test", "test", 1_000_000L, "")
        val itemNormal = NotificationListItem.Item(notif1, isSelectionMode = false, isSelected = false)

        submitListAndSync(adapter, listOf(itemNormal))
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 100, 100)

        val holder1 = recyclerView.findViewHolderForAdapterPosition(0)!!

        // Rebind same notification id to selection mode = true
        val itemSelection = NotificationListItem.Item(notif1, isSelectionMode = true, isSelected = true)
        submitListAndSync(adapter, listOf(itemSelection))
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 100, 100)

        val holder2 = recyclerView.findViewHolderForAdapterPosition(0)!!
        assertEquals(holder1, holder2)

        holder2.itemView.performClick()
        verify(exactly = 1) { onToggleSelection("1") }
        verify(exactly = 0) { onNotificationClick(any()) }

        val longClickResult = holder2.itemView.performLongClick()
        assertFalse(longClickResult)

        // Rebind back to normal mode with updated notification text
        val notif2 = Notification("1", "Rebound Text", false, "test", "test", 1_000_000L, "")
        val itemNormalRebound = NotificationListItem.Item(notif2, isSelectionMode = false, isSelected = false)
        submitListAndSync(adapter, listOf(itemNormalRebound))
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 100, 100)

        val holder3 = recyclerView.findViewHolderForAdapterPosition(0)!!
        assertEquals(holder1, holder3)

        holder3.itemView.performClick()
        verify(exactly = 1) { onNotificationClick(match { it.formattedText == "Rebound Text" }) }
    }

    @Test
    fun testReadNotification_markAsReadClickDoesNotTriggerCallback() {
        val onMarkAsReadClick = mockk<(String) -> Unit>(relaxed = true)
        val adapter = NotificationsAdapter(
            onMarkAsReadClick = onMarkAsReadClick,
            onNotificationClick = {},
            onToggleSelection = {},
            onToggleGroupExpansion = {},
            now = { 1_000_000L }
        )

        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.itemAnimator = null
        recyclerView.adapter = adapter

        val readNotif = Notification("1", "Read Text", isRead = true, "test", "test", 1_000_000L, "")
        val item = NotificationListItem.Item(readNotif, isSelectionMode = false, isSelected = false)

        submitListAndSync(adapter, listOf(item))
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 100, 100)

        val holder = recyclerView.findViewHolderForAdapterPosition(0)!!
        val btnMarkAsRead = holder.itemView.findViewById<View>(R.id.btn_mark_as_read)
        btnMarkAsRead.performClick()

        verify(exactly = 0) { onMarkAsReadClick(any()) }
    }
}
