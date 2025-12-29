package org.ole.planet.myplanet.ui.components

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSpinner

class CustomSpinner(context: Context, attrs: AttributeSet?) : AppCompatSpinner(context, attrs) {
    private var listener: OnItemSelectedListener? = null

    override fun setSelection(position: Int) {
        super.setSelection(position)
        listener?.onItemSelected(null, null, position, 0)
    }

    fun onSameItemSelected(listener: OnItemSelectedListener) {
        this.listener = listener
    }
}
