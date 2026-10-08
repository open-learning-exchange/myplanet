package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.core.text.HtmlCompat
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseBindingFragment
import org.ole.planet.myplanet.databinding.FragmentDisclaimerBinding

class DisclaimerFragment : BaseBindingFragment<FragmentDisclaimerBinding>(FragmentDisclaimerBinding::inflate) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvDisclaimer.text = HtmlCompat.fromHtml(getString(R.string.disclaimer), HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.tvDisclaimer.movementMethod = LinkMovementMethod.getInstance()
    }
}
