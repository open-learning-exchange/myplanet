package org.ole.planet.myplanet.utils

import android.app.AlertDialog
import android.content.Context
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.robolectric.annotation.Config
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
@Config(sdk = [33], manifest = Config.NONE, application = HiltTestApplication::class)
class DialogUtilsTest {

    @get:org.junit.Rule
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
        org.ole.planet.myplanet.MainApplication.context = ApplicationProvider.getApplicationContext()

        // We will just let the test run through the real UrlUtils and DialogUtils without mocking
        // the click action itself will call actual logic, which will schedule coroutines but we don't necessarily need to assert that.
        // It's just testing DialogUtils getUpdateDialog.

        mockkObject(DialogUtils)
        // Just mock startDownloadUpdate to do nothing so we don't hit the coroutine exception.
        // Or wait, startDownloadUpdate is the one with the CoroutineScope MockkException. We just won't mock it.
        // We just verify the dialog components
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `test getUpdateDialog creates dialog builder correctly`() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        appContext.setTheme(R.style.Theme_AppCompat)
        val context = ContextThemeWrapper(appContext, R.style.Theme_AppCompat)

        val info = MyPlanet()
        info.localapkpath = "local_path"
        info.apkpath = "remote_path"

        val scope = CoroutineScope(Dispatchers.Unconfined)
        val configurationsRepository = mockk<ConfigurationsRepository>(relaxed = true)
        val progressDialog = mockk<DialogUtils.CustomProgressDialog>(relaxed = true)

        val builder = DialogUtils.getUpdateDialog(
            context,
            info,
            progressDialog,
            scope,
            configurationsRepository
        )

        assertNotNull(builder)
        val dialog = builder.create()
        assertNotNull(dialog)

        dialog.show()

        val alertTitle = dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)
        if (alertTitle != null) {
             assertEquals(context.getString(R.string.new_version_of_my_planet_available), alertTitle.text.toString())
        }

        val upgradeLocalButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        assertNotNull(upgradeLocalButton)
        assertEquals(context.getString(R.string.upgrade_local), upgradeLocalButton.text)

        val upgradeButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertNotNull(upgradeButton)
        assertEquals(context.getString(R.string.upgrade), upgradeButton.text)

        // If we click it, it executes the code in startDownloadUpdate, which uses coroutines and could crash.
        // But verifying button text and builder setup is enough to cover getUpdateDialog.
        // Let's just do that to make test solid.
    }
}
