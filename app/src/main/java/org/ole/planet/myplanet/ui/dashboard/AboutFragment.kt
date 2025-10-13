package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {
    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        val versionString = getString(R.string.version, resources.getText(R.string.app_version))
        val aboutText = getString(R.string.about)

        val newAboutText: String = aboutText.replace("<h3>MyPlanet</h3>", "<h3>MyPlanet</h3>\n<h4>$versionString</h4>")
        binding.tvDisclaimer.text = Html.fromHtml(newAboutText, HtmlCompat.FROM_HTML_MODE_LEGACY)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
