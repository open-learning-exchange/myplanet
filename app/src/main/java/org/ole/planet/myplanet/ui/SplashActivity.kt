package org.ole.planet.myplanet.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.ole.planet.myplanet.databinding.ActivitySplashBinding
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.autoSynFeature
import org.ole.planet.myplanet.utilities.FileUtils.availableOverTotalMemoryFormattedString
import org.ole.planet.myplanet.utilities.FileUtils.copyAssets
import org.ole.planet.myplanet.utilities.SharedPrefManager

class SplashActivity : AppCompatActivity() {
    private var binding: ActivitySplashBinding? = null
    private var prefData: SharedPrefManager? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        prefData = SharedPrefManager(this)

        // Find and show space available on the device
        binding!!.tvAvailableSpace.text = availableOverTotalMemoryFormattedString
        copyAssets(this)
        val settings = getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE)
        if (settings.getBoolean(Constants.KEY_LOGIN, false) && !autoSynFeature(Constants.KEY_AUTOSYNC_, applicationContext)) {
            val dashboard = Intent(applicationContext, DashboardActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(dashboard)
            finish()
            return
        }
        if (prefData!!.getFIRSTLAUNCH()) {
            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            finish()
        }
        binding!!.getStarted.setOnClickListener {
            prefData!!.setFIRSTLAUNCH(true)
            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
        }
    }
}
