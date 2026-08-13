package org.ole.planet.myplanet.ui.viewer

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.io.File
import org.ole.planet.myplanet.R
import org.robolectric.shadows.ShadowLooper
import kotlinx.coroutines.delay

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class ResourceViewerFragmentTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    private lateinit var tempFile: File

    @Before
    fun setUp() {
        hiltRule.inject()
        tempFile = File(ApplicationProvider.getApplicationContext<android.content.Context>().getExternalFilesDir(null), "ole/test.txt")
        tempFile.parentFile?.mkdirs()
        tempFile.writeText("Test Content")
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    @Test
    fun setupTextViewer_rendersTextContentFromFile() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), ResourceViewerActivity::class.java).apply {
            putExtra("TOUCHED_FILE", "test.txt")
            putExtra("RESOURCE_TITLE", "Test Title")
            putExtra("resourceType", ResourceViewerFragment.ResourceType.TEXT.name)
        }

        val activityController = Robolectric.buildActivity(ResourceViewerActivity::class.java, intent).setup()
        val activity = activityController.get()

        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as ResourceViewerFragment

        ShadowLooper.idleMainLooper()
        // Wait for coroutine if it is dispatched
        Thread.sleep(100)
        ShadowLooper.idleMainLooper()

        val textContent = fragment.view?.findViewById<TextView>(R.id.textContent)
        assertEquals("Test Content", textContent?.text.toString())
    }
}
