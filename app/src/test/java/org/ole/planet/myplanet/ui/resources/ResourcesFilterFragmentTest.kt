package org.ole.planet.myplanet.ui.resources

import android.app.Application
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class ResourcesFilterFragmentTest {

    @Test
    fun `test fragment inflates and iv_close is present`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)

        val fragment = ResourcesFilterFragment()
        fragment.show(activity.supportFragmentManager, "filter_dialog")
        activity.supportFragmentManager.executePendingTransactions()

        val view = fragment.view
        assertNotNull(view)
        val ivClose = view?.findViewById<ImageView>(R.id.iv_close)
        assertNotNull(ivClose)
    }
}
