package org.ole.planet.myplanet.ui.user

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentEditAchievementBinding
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EditAchievementFragmentTest {

    @Test
    fun testToolbarNavigationIconAndContentDescription() {
        val baseContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val themedContext = ContextThemeWrapper(baseContext, com.google.android.material.R.style.Theme_MaterialComponents)
        val inflater = LayoutInflater.from(themedContext)
        val binding = FragmentEditAchievementBinding.inflate(inflater, null, false)

        assertNotNull(binding.toolbar)
        assertNotNull(binding.toolbar.navigationIcon)
        assertEquals(baseContext.getString(R.string.btn_back), binding.toolbar.navigationContentDescription)
    }

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `copyCvFile writes the picked file`() {
        val dest = File(tempFolder.root, "ach1/cv.pdf")

        EditAchievementFragment.copyCvFile({ ByteArrayInputStream("new cv".toByteArray()) }, dest)

        assertEquals("new cv", dest.readText())
        assertFalse(File(dest.path + ".part").exists())
    }

    @Test
    fun `copyCvFile throws and keeps the existing CV when the source can't be opened`() {
        val dest = tempFolder.newFile("cv.pdf").apply { writeText("old cv") }

        assertThrows(IOException::class.java) {
            EditAchievementFragment.copyCvFile({ null }, dest)
        }
        assertEquals("old cv", dest.readText())
    }

    @Test
    fun `copyCvFile throws and keeps the existing CV when reading fails midway`() {
        val dest = tempFolder.newFile("cv.pdf").apply { writeText("old cv") }
        val failing = object : InputStream() {
            private var sent = 0
            override fun read(): Int = if (sent++ < 3) 'x'.code else throw IOException("stream broke")
        }

        assertThrows(IOException::class.java) {
            EditAchievementFragment.copyCvFile({ failing }, dest)
        }
        assertEquals("old cv", dest.readText())
        assertFalse(File(dest.path + ".part").exists())
    }
}
