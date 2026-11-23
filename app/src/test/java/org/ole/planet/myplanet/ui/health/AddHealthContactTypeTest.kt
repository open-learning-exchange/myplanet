package org.ole.planet.myplanet.ui.health

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "es")
class AddHealthContactTypeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `keys are English whatever the device language`() {
        assertEquals(listOf("Phone", "Email"), AddHealthActivity.contactTypeKeys(context).toList())
    }

    @Test
    fun `stored English value is shown in the device language`() {
        assertEquals("Teléfono", AddHealthActivity.contactTypeLabel(context, "Phone"))
    }

    @Test
    fun `unknown or older translated values are shown as stored`() {
        assertEquals("Teléfono", AddHealthActivity.contactTypeLabel(context, "Teléfono"))
        assertEquals("WhatsApp", AddHealthActivity.contactTypeLabel(context, "WhatsApp"))
    }
}
