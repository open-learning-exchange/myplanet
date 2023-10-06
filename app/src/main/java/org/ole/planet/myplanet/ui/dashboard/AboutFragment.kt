package org.ole.planet.myplanet.ui.dashboard

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.databinding.FragmentAboutBinding
import org.ole.planet.myplanet.utilities.Constants

class AboutFragment : Fragment() {
    private lateinit var fragmentAboutBinding: FragmentAboutBinding
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentAboutBinding = FragmentAboutBinding.inflate(inflater, container, false)
        fragmentAboutBinding.tvDisclaimer.text = Html.fromHtml(getString(Constants.ABOUT), HtmlCompat.FROM_HTML_MODE_LEGACY)
        return fragmentAboutBinding.root
    }
}