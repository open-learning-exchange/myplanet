package org.ole.planet.myplanet.ui.dashboard

import android.os.Build
import android.os.Bundle
import android.text.Html
import androidx.annotation.RequiresApi
import androidx.core.text.HtmlCompat
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.databinding.ActivityAboutBinding
import org.ole.planet.myplanet.utilities.Constants

class AboutActivity : BaseActivity() {
    private lateinit var activityAboutBinding: ActivityAboutBinding
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityAboutBinding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(activityAboutBinding.root)
        initActionBar()
        activityAboutBinding.tvDisclaimer.text = Html.fromHtml(getString(Constants.ABOUT), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
}
