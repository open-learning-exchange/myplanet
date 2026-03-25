package org.ole.planet.myplanet.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class IntentUtilsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun `test openAudioFile`() {
        val context = mockk<Context>(relaxed = true)
        val intentSlot = slot<Intent>()
        every { context.startActivity(capture(intentSlot)) } returns Unit

        IntentUtils.openAudioFile(context, "path/to/audio.mp3", "My Audio")

        verify(exactly = 1) { context.startActivity(any()) }
        val capturedIntent = intentSlot.captured
        assertEquals(org.ole.planet.myplanet.ui.viewer.AudioPlayerActivity::class.java.name, capturedIntent.component?.className)
        assertTrue(capturedIntent.getBooleanExtra("isFullPath", false))
        assertEquals("path/to/audio.mp3", capturedIntent.getStringExtra("TOUCHED_FILE"))
        assertEquals("My Audio", capturedIntent.getStringExtra("RESOURCE_TITLE"))
    }

    @Test
    fun `test openPlayStore normal`() {
        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "com.example.app"
        val intentSlot = slot<Intent>()
        every { context.startActivity(capture(intentSlot)) } returns Unit

        IntentUtils.openPlayStore(context)

        verify(exactly = 1) { context.startActivity(any()) }
        val capturedIntent = intentSlot.captured
        assertEquals(Intent.ACTION_VIEW, capturedIntent.action)
        assertEquals("market://details?id=com.example.app", capturedIntent.data.toString())
        assertTrue((capturedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun `test openPlayStore with ActivityNotFoundException`() {
        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "com.example.app"
        val intents = mutableListOf<Intent>()

        every { context.startActivity(capture(intents)) } throws ActivityNotFoundException() andThen Unit

        IntentUtils.openPlayStore(context)

        verify(exactly = 2) { context.startActivity(any()) }
        assertEquals(2, intents.size)

        val firstIntent = intents[0]
        assertEquals(Intent.ACTION_VIEW, firstIntent.action)
        assertEquals("market://details?id=com.example.app", firstIntent.data.toString())
        assertTrue((firstIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)

        val secondIntent = intents[1]
        assertEquals(Intent.ACTION_VIEW, secondIntent.action)
        assertEquals("https://play.google.com/store/apps/details?id=com.example.app", secondIntent.data.toString())
        assertTrue((secondIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }
}
