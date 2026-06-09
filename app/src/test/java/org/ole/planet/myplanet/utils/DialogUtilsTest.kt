package org.ole.planet.myplanet.utils

import android.app.AlertDialog
import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class DialogUtilsTest {

    @Before
    fun setup() {
        MainApplication.context = ApplicationProvider.getApplicationContext()

        mockkStatic(DialogUtils::class)
        every { DialogUtils.startDownloadUpdate(any(), any(), any(), any(), any()) } returns Unit
        every { DialogUtils.getUpdateDialog(any(), any(), any(), any(), any()) } answers { callOriginal() }

        mockkStatic(UrlUtils::class)
        every { UrlUtils.getApkUpdateUrl(any<String>()) } returns "mocked_local_path"
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
        val shadowDialog = org.robolectric.Shadows.shadowOf(dialog)

        assertEquals(context.getString(R.string.new_version_of_my_planet_available), shadowDialog.title)

        val upgradeLocalButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        assertNotNull(upgradeLocalButton)
        assertEquals(context.getString(R.string.upgrade_local), upgradeLocalButton.text)

        val upgradeButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertNotNull(upgradeButton)
        assertEquals(context.getString(R.string.upgrade), upgradeButton.text)

        upgradeLocalButton.performClick()
        verify(exactly = 1) { DialogUtils.startDownloadUpdate(context, "mocked_local_path", progressDialog, scope, configurationsRepository) }

        upgradeButton.performClick()
        verify(exactly = 1) { DialogUtils.startDownloadUpdate(context, "remote_path", progressDialog, scope, configurationsRepository) }
    }
}
