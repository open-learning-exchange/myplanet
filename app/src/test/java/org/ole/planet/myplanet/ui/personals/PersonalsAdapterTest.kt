package org.ole.planet.myplanet.ui.personals

import android.app.Application
import android.content.Context
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.Personal
import org.ole.planet.myplanet.utils.TimeUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PersonalsAdapterTest {

    private lateinit var adapter: PersonalsAdapter
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        adapter = PersonalsAdapter(context)
        mockkObject(TimeUtils)
    }

    @After
    fun tearDown() {
        unmockkObject(TimeUtils)
    }

    @Test
    fun `onBindViewHolder caches formatted date when binding same item twice`() {
        val timestamp = 1600000000000L
        val personalItem = Personal().apply {
            id = "1"
            _id = "1"
            title = "Test Personal"
            description = "Test Description"
            date = timestamp
        }

        every { TimeUtils.getFormattedDate(timestamp) } returns "Formatted Date String"

        adapter.submitList(listOf(personalItem))

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(holder, 0)
        assertEquals("Formatted Date String", holder.binding.date.text.toString())

        adapter.onBindViewHolder(holder, 0)
        assertEquals("Formatted Date String", holder.binding.date.text.toString())

        verify(exactly = 1) { TimeUtils.getFormattedDate(timestamp) }
    }
}
