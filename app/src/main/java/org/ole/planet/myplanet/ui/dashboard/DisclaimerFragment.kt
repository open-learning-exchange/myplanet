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

    private var fragmentDisclaimerBinding: FragmentDisclaimerBinding? = null
    private val binding get() = fragmentDisclaimerBinding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fragmentDisclaimerBinding = FragmentDisclaimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val disclaimerText = getString(R.string.disclaimer)
        binding.tvDisclaimer.text = HtmlCompat.fromHtml(disclaimerText, HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.tvDisclaimer.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fragmentDisclaimerBinding = null
    }
}