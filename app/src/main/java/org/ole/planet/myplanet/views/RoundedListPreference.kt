package org.ole.planet.myplanet.views

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import androidx.preference.ListPreference
import org.ole.planet.myplanet.R

class RoundedListPreference(context: Context, attrs: AttributeSet) : ListPreference(context, attrs) {

    override fun onClick() {
        val currentIndex = findIndexOfValue(value)
        val dialog = AlertDialog.Builder(ContextThemeWrapper(context, R.style.CustomAlertDialogStyle))
            .setTitle(title)
            .setSingleChoiceItems(entryValues, currentIndex) { dialog: DialogInterface, index: Int ->
                if (callChangeListener(entryValues[index].toString())) {
                    setValueIndex(index)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }
}
