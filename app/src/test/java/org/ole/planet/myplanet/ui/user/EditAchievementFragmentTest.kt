package org.ole.planet.myplanet.ui.user

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
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
}
