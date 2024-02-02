package org.ole.planet.myplanet.base

import android.R
import android.os.Bundle
import androidx.fragment.app.DialogFragment

abstract class BaseDialogFragment : DialogFragment() {
    @JvmField
    var id: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Holo_Light_Dialog_NoActionBar_MinWidth)
        if (arguments != null) {
            id = requireArguments().getString(key)
        }
    }

    protected abstract val key: String?
}
