package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentDisclaimerBinding

class DisclaimerFragment : Fragment() {
    private lateinit var fragmentDisclaimerBinding: FragmentDisclaimerBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentDisclaimerBinding = FragmentDisclaimerBinding.inflate(inflater, container, false)
        return fragmentDisclaimerBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentDisclaimerBinding.tvDisclaimer.text = HtmlCompat.fromHtml(getString(R.string.disclaimer), HtmlCompat.FROM_HTML_MODE_LEGACY)
        fragmentDisclaimerBinding.tvDisclaimer.movementMethod = LinkMovementMethod.getInstance()
    }
}