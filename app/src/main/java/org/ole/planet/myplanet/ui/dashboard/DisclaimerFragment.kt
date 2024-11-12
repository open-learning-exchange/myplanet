package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.databinding.FragmentDisclaimerBinding
import org.ole.planet.myplanet.utilities.Constants

class DisclaimerFragment : Fragment() {
    private lateinit var fragmentDisclaimerBinding: FragmentDisclaimerBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentDisclaimerBinding = FragmentDisclaimerBinding.inflate(inflater, container, false)
        fragmentDisclaimerBinding.tvDisclaimer.text = Html.fromHtml(getString(Constants.DISCLAIMER), HtmlCompat.FROM_HTML_MODE_LEGACY)
        return fragmentDisclaimerBinding.root
    }
}