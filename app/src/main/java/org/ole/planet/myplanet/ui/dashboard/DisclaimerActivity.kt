package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.text.Html
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.databinding.ActivityDisclaimerBinding
import org.ole.planet.myplanet.utilities.Constants

class DisclaimerActivity : BaseActivity() {
    lateinit var binding: ActivityDisclaimerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDisclaimerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActionBar()
        binding.tvDisclaimer.text = Html.fromHtml(Constants.DISCLAIMER)
    }
}
