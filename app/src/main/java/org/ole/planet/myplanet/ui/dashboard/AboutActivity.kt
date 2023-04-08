package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.text.Html
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.databinding.ActivityAboutBinding
import org.ole.planet.myplanet.utilities.Constants

class AboutActivity : BaseActivity() {
    lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActionBar()
        binding.tvDisclaimer.text = Html.fromHtml(Constants.ABOUT)
    }
}
