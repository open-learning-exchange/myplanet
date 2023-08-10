package org.ole.planet.myplanet.ui.dashboard

import android.os.Build
import android.os.Bundle
import android.text.Html
import androidx.annotation.RequiresApi
import androidx.core.text.HtmlCompat
import kotlinx.android.synthetic.main.activity_disclaimer.tv_disclaimer
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.utilities.Constants

class DisclaimerActivity : BaseActivity() {
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_disclaimer)
        initActionBar()
        tv_disclaimer.text = Html.fromHtml(getString(Constants.DISCLAIMER), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
}
