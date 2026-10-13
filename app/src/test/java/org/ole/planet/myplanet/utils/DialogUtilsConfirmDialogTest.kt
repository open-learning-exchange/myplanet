package org.ole.planet.myplanet.utils

import android.app.Activity
import android.app.Application
import androidx.appcompat.app.AlertDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utils.DialogUtils.confirmDialog
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DialogUtilsConfirmDialogTest {

    private lateinit var activity: Activity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    @Test
    fun `defaults positive and negative button text to yes and no`() {
        val dialog = activity.confirmDialog(message = "Are you sure?")

        assertEquals(activity.getString(R.string.yes), dialog.getButton(AlertDialog.BUTTON_POSITIVE).text)
        assertEquals(activity.getString(R.string.no), dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text)
    }

    @Test
    fun `uses custom button text when provided`() {
        val dialog = activity.confirmDialog(message = "Leave team?", positiveText = "Leave", negativeText = "Stay")

        assertEquals("Leave", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text)
        assertEquals("Stay", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text)
    }

    @Test
    fun `content description defaults to the button label`() {
        val dialog = activity.confirmDialog(message = "Are you sure?", positiveText = "Delete", negativeText = "Keep")

        assertEquals("Delete", dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription)
        assertEquals("Keep", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription)
    }

    @Test
    fun `content description can be overridden independently of the label`() {
        val dialog = activity.confirmDialog(
            message = "Are you sure?",
            positiveText = "Yes",
            positiveContentDescription = "Confirm removal",
            negativeText = "No",
            negativeContentDescription = "Keep item"
        )

        assertEquals("Confirm removal", dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription)
        assertEquals("Keep item", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription)
    }

    @Test
    fun `tapping positive button dismisses the dialog and invokes onPositive`() {
        var invoked = false
        val dialog = activity.confirmDialog(message = "Are you sure?", onPositive = { invoked = true })

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()

        assertTrue(invoked)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun `tapping negative button dismisses the dialog and invokes onNegative without invoking onPositive`() {
        var positiveInvoked = false
        var negativeInvoked = false
        val dialog = activity.confirmDialog(
            message = "Are you sure?",
            onPositive = { positiveInvoked = true },
            onNegative = { negativeInvoked = true }
        )

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()

        assertTrue(negativeInvoked)
        assertFalse(positiveInvoked)
        assertFalse(dialog.isShowing)
    }
}
