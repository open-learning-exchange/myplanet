package org.ole.planet.myplanet.ui.dashboard

import android.os.Build
import android.os.Bundle
import android.text.Html
import androidx.annotation.RequiresApi
import androidx.core.text.HtmlCompat
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.databinding.ActivityDisclaimerBinding
import org.ole.planet.myplanet.utilities.Constants

class DisclaimerActivity : BaseActivity() {
    private lateinit var activityDisclaimerBinding: ActivityDisclaimerBinding
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityDisclaimerBinding = ActivityDisclaimerBinding.inflate(layoutInflater)
        setContentView(activityDisclaimerBinding.root)
        initActionBar()
        activityDisclaimerBinding.tvDisclaimer.text = Html.fromHtml(getString(Constants.DISCLAIMER), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
}