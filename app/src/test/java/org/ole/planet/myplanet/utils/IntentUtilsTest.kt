package org.ole.planet.myplanet.utils

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.ui.viewer.ResourceViewerActivity
import org.ole.planet.myplanet.ui.viewer.ResourceViewerFragment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class IntentUtilsTest {

    @Test
    fun `test openAudioFile`() {
        val context = mockk<Context>(relaxed = true)
        val intentSlot = slot<Intent>()
        every { context.startActivity(capture(intentSlot)) } returns Unit

        IntentUtils.openAudioFile(context, "path/to/audio.mp3", "My Audio")

        verify(exactly = 1) { context.startActivity(any()) }
        val capturedIntent = intentSlot.captured
        assertEquals(ResourceViewerActivity::class.java.name, capturedIntent.component?.className)
        assertEquals(ResourceViewerFragment.ResourceType.AUDIO.name, capturedIntent.getStringExtra("resourceType"))
        assertTrue(capturedIntent.getBooleanExtra("isFullPath", false))
        assertEquals("path/to/audio.mp3", capturedIntent.getStringExtra("TOUCHED_FILE"))
        assertEquals("My Audio", capturedIntent.getStringExtra("RESOURCE_TITLE"))
    }

    @Test
    fun `test openAppSettings`() {
        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "com.example.app"
        val intentSlot = slot<Intent>()
        every { context.startActivity(capture(intentSlot)) } returns Unit

        IntentUtils.openAppSettings(context)

        verify(exactly = 1) { context.startActivity(any()) }
        val capturedIntent = intentSlot.captured
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, capturedIntent.action)
        assertEquals("package:com.example.app", capturedIntent.data.toString())
        assertTrue((capturedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun `test openAppSettings with ActivityNotFoundException`() {
        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "com.example.app"
        val intents = mutableListOf<Intent>()

        every { context.startActivity(capture(intents)) } throws ActivityNotFoundException() andThen Unit

        IntentUtils.openAppSettings(context)

        verify(exactly = 2) { context.startActivity(any()) }
        assertEquals(2, intents.size)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intents[0].action)
        assertEquals(Settings.ACTION_SETTINGS, intents[1].action)
        assertTrue((intents[1].flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }
}
