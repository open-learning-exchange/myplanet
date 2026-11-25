package org.ole.planet.myplanet.ui.resources

import android.content.Intent
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.model.MyLibrary
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AddResourceActivityRotationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var myLibraryDao: MyLibraryDao
    
    private val controllers = mutableListOf<ActivityController<AddResourceActivity>>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        controllers.forEach { it.pause().stop().destroy() }
    }

    private fun launch(intent: Intent) =
        Robolectric.buildActivity(AddResourceActivity::class.java, intent).setup().also { controllers += it }

    private fun intent(block: Intent.() -> Unit = {}) =
        Intent(ApplicationProvider.getApplicationContext(), AddResourceActivity::class.java).apply(block)

    @Test
    fun `add mode keeps chosen levels and subjects across rotation`() {
        val controller = launch(intent())
        controller.get().apply {
            levels?.add("Lower Primary")
            subjects?.add("Mathematics")
            findViewById<EditText>(R.id.et_title).setText("My notes")
        }

        controller.recreate()
        ShadowLooper.idleMainLooper()
        val restored = controller.get()

        assertEquals(listOf("Lower Primary"), restored.levels)
        assertEquals(listOf("Mathematics"), restored.subjects)
        assertEquals("My notes", restored.findViewById<EditText>(R.id.et_title).text.toString())
        assertEquals("Lower Primary", restored.findViewById<TextView>(R.id.tv_levels).text.toString())
    }

    @Test
    fun `edit mode keeps the user's edits instead of reloading from the database`() {
        runBlocking {
            myLibraryDao.upsert(MyLibrary().apply {
                id = "res1"
                title = "Saved title"
                description = "Saved description"
            })
        }
        val controller = launch(intent {
            putExtra("resource_id", "res1")
            putExtra("is_edit_mode", true)
        })
        val titleField = controller.get().findViewById<EditText>(R.id.et_title)
        while (titleField.text.toString() != "Saved title") {
            ShadowLooper.idleMainLooper()
        }
        titleField.setText("Edited title")

        controller.recreate()
        repeat(5) { ShadowLooper.idleMainLooper() }

        assertEquals("Edited title", controller.get().findViewById<EditText>(R.id.et_title).text.toString())
    }
}
