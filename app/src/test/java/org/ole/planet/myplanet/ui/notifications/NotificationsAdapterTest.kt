package org.ole.planet.myplanet.ui.notifications

import android.content.Context
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltTestApplication
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.Notification
import org.ole.planet.myplanet.model.NotificationListItem
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class NotificationsAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: NotificationsAdapter
    private val fixedNow = 1_700_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        adapter = NotificationsAdapter(
            onMarkAsReadClick = {},
            onNotificationClick = {},
            onToggleSelection = {},
            onToggleGroupExpansion = {},
            now = { fixedNow }
        )
    }

    private fun bindAndGetTimestamp(createdAt: Long): String {
        val notification = Notification("1", "test", false, "test", "test", createdAt, "")
        val item = NotificationListItem.Item(notification, false, false)

        val parent = FrameLayout(context)
        val viewHolder = adapter.onCreateViewHolder(parent, 1) as NotificationsAdapter.ItemViewHolder

        viewHolder.bind(item)

        val timestampTextView = viewHolder.itemView.findViewById<TextView>(R.id.timestamp)
        return timestampTextView.text.toString()
    }

    @Test
    fun `test formatRelativeTime just now boundary`() {
        // diff = 0ms (exact match with fixedNow) -> just now
        assertEquals(context.getString(R.string.just_now), bindAndGetTimestamp(fixedNow))
        // diff = 59_999ms (upper boundary for just now) -> just now
        assertEquals(context.getString(R.string.just_now), bindAndGetTimestamp(fixedNow - 59_999L))
    }

    @Test
    fun `test formatRelativeTime minutes ago boundary`() {
        // diff = 60_000ms (1 min boundary) -> 1 minute ago
        assertEquals(context.getString(R.string.minutes_ago, 1L), bindAndGetTimestamp(fixedNow - 60_000L))
        // diff = 3_599_999ms (59 min boundary) -> 59 minutes ago
        assertEquals(context.getString(R.string.minutes_ago, 59L), bindAndGetTimestamp(fixedNow - 3_599_999L))
    }

    @Test
    fun `test formatRelativeTime hours ago boundary`() {
        // diff = 3_600_000ms (1 hr boundary) -> 1 hour ago
        assertEquals(context.getString(R.string.hours_ago, 1L), bindAndGetTimestamp(fixedNow - 3_600_000L))
        // diff = 86_399_999ms (23 hr 59 min 59 sec boundary) -> 23 hours ago
        assertEquals(context.getString(R.string.hours_ago, 23L), bindAndGetTimestamp(fixedNow - 86_399_999L))
    }

    @Test
    fun `test formatRelativeTime yesterday boundary`() {
        // diff = 86_400_000ms (24 hr boundary) -> yesterday
        assertEquals(context.getString(R.string.yesterday), bindAndGetTimestamp(fixedNow - 86_400_000L))
        // diff = 172_799_999ms (47 hr 59 min 59 sec boundary) -> yesterday
        assertEquals(context.getString(R.string.yesterday), bindAndGetTimestamp(fixedNow - 172_799_999L))
    }

    @Test
    fun `test formatRelativeTime days ago boundary`() {
        // diff = 172_800_000ms (2 days boundary) -> 2 days ago
        assertEquals(context.getString(R.string.days_ago, 2L), bindAndGetTimestamp(fixedNow - 172_800_000L))
        // diff = 604_799_999ms (6 days 23 hr 59 min 59 sec boundary) -> 6 days ago
        assertEquals(context.getString(R.string.days_ago, 6L), bindAndGetTimestamp(fixedNow - 604_799_999L))
    }

    @Test
    fun `test formatRelativeTime fallback absolute date boundary`() {
        // diff = 604_800_000ms (7 days boundary) -> fallback date format
        val createdAt = fixedNow - 604_800_000L
        val expectedString = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(createdAt))
        assertEquals(expectedString, bindAndGetTimestamp(createdAt))
    }

    @Test
    fun `test bind updates title when formattedText changes for same notification id`() {
        val parent = FrameLayout(context)
        val viewHolder = adapter.onCreateViewHolder(parent, 1) as NotificationsAdapter.ItemViewHolder
        val titleTextView = viewHolder.itemView.findViewById<TextView>(R.id.title)

        val initialNotif = Notification("1", "<b>Initial</b> text", false, "test", "test", System.currentTimeMillis(), "")
        viewHolder.bind(NotificationListItem.Item(initialNotif))
        assertEquals("Initial text", titleTextView.text.toString())

        val updatedNotif = Notification("1", "<b>Updated</b> text", false, "test", "test", System.currentTimeMillis(), "")
        viewHolder.bind(NotificationListItem.Item(updatedNotif))
        assertEquals("Updated text", titleTextView.text.toString())
    }
}
