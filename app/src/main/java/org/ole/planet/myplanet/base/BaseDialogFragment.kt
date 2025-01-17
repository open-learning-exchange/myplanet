package org.ole.planet.myplanet.base

import org.ole.planet.myplanet.R
import android.os.Bundle
import androidx.fragment.app.DialogFragment

abstract class BaseDialogFragment : DialogFragment() {
    var id: String? = null
    var teamId: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.AppTheme_Dialog_NoActionBar_MinWidth)
        if (arguments != null) {
            id = requireArguments().getString(key)
            teamId = requireArguments().getString("teamId")
        }
    }

    protected abstract val key: String?
}
