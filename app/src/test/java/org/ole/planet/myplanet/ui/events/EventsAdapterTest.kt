package org.ole.planet.myplanet.ui.events

import android.app.Application
import android.content.Context
import android.os.Build
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.RealmMeetup
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], application = Application::class)
class EventsAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: EventsAdapter

    private var clickedMeetup: RealmMeetup? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        adapter = EventsAdapter { meetup ->
            clickedMeetup = meetup
        }
    }

    @Test
    fun `test adapter item binding and partial payload diff`() {
        val oldMeetup = RealmMeetup().apply {
            id = "1"
            title = "Old Title"
            description = "Old Desc"
            startDate = 1000L
            endDate = 2000L
            startTime = "10:00"
            endTime = "11:00"
            meetupLocation = "Old Location"
            meetupLink = "Old Link"
            recurring = "none"
            creator = "Old Creator"
        }

        var committed = false
        adapter.submitList(listOf(oldMeetup)) {
            committed = true
        }

        while (!committed) {
            ShadowLooper.idleMainLooper()
        }

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        assertEquals("Old Title", holder.binding.tvTitle.text.toString())
        assertEquals("Old Desc", holder.binding.tvDescription.text.toString())
        assertEquals("Old Location", holder.binding.tvLocation.text.toString())

        val newMeetup = RealmMeetup().apply {
            id = "1"
            title = "New Title"
            description = "Old Desc"
            startDate = 1000L
            endDate = 2000L
            startTime = "10:00"
            endTime = "11:00"
            meetupLocation = "New Location"
            meetupLink = "Old Link"
            recurring = "none"
            creator = "Old Creator"
        }

        var updatedCommitted = false
        adapter.submitList(listOf(newMeetup)) {
            updatedCommitted = true
        }

        while (!updatedCommitted) {
            ShadowLooper.idleMainLooper()
        }

        // Apply partial bind
        adapter.onBindViewHolder(holder, 0, mutableListOf(setOf("TITLE", "MEETUP_LOCATION")))

        assertEquals("New Title", holder.binding.tvTitle.text.toString())
        assertEquals("New Location", holder.binding.tvLocation.text.toString())
        assertEquals("Old Desc", holder.binding.tvDescription.text.toString())

        holder.binding.root.performClick()
        assertEquals("New Title", clickedMeetup?.title)
        assertEquals("New Location", clickedMeetup?.meetupLocation)
    }
}
