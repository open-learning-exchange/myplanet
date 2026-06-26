package org.ole.planet.myplanet.ui.notifications

import android.content.Context
import android.os.Build
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
@Config(sdk = [Build.VERSION_CODES.O_MR1], application = dagger.hilt.android.testing.HiltTestApplication::class)
class NotificationsAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: NotificationsAdapter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        adapter = NotificationsAdapter(
            onMarkAsReadClick = {},
            onNotificationClick = {},
            onToggleSelection = {},
            onToggleGroupExpansion = {}
        )
    }

    private fun bindAndGetTimestamp(createdAtDiff: Long): String {
        val now = System.currentTimeMillis()
        val notification = Notification("1", "test", false, "test", "test", now - createdAtDiff, "")
        val item = NotificationListItem.Item(notification, false, false)

        val parent = FrameLayout(context)
        val viewHolder = adapter.onCreateViewHolder(parent, 1) as NotificationsAdapter.ItemViewHolder

        viewHolder.bind(item)

        val timestampTextView = viewHolder.itemView.findViewById<TextView>(R.id.timestamp)
        return timestampTextView.text.toString()
    }

    @Test
    fun `test formatRelativeTime just now`() {
        assertEquals(context.getString(R.string.just_now), bindAndGetTimestamp(10_000L))
    }

    @Test
    fun `test formatRelativeTime minutes ago`() {
        assertEquals(context.getString(R.string.minutes_ago, 5L), bindAndGetTimestamp(5 * 60_000L))
    }

    @Test
    fun `test formatRelativeTime hours ago`() {
        assertEquals(context.getString(R.string.hours_ago, 2L), bindAndGetTimestamp(2 * 3_600_000L))
    }

    @Test
    fun `test formatRelativeTime yesterday`() {
        assertEquals(context.getString(R.string.yesterday), bindAndGetTimestamp(1 * 86_400_000L + 1000L))
    }

    @Test
    fun `test formatRelativeTime days ago`() {
        assertEquals(context.getString(R.string.days_ago, 3L), bindAndGetTimestamp(3 * 86_400_000L + 1000L))
    }

    @Test
    fun `test formatRelativeTime absolute date`() {
        val diff = 8 * 86_400_000L
        val expectedDate = Date(System.currentTimeMillis() - diff)
        val expectedString = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(expectedDate)
        assertEquals(expectedString, bindAndGetTimestamp(diff))
    }
}
