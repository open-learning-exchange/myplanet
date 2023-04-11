package org.ole.planet.myplanet.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.FileUtils

class SplashActivity : AppCompatActivity() {

    private lateinit var rbChild: RadioButton
    private lateinit var rbNormal: RadioButton
    private lateinit var getStarted: Button
    private lateinit var tvAvailableSpace: TextView
    private lateinit var splashViewModel: SplashViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        rbChild = findViewById(R.id.child_login)
        rbNormal = findViewById(R.id.normal_login)
        getStarted = findViewById(R.id.get_started)

        // Find and show space available on the device
        tvAvailableSpace = findViewById(R.id.tv_available_space)
        tvAvailableSpace.text = FileUtils.getAvailableOverTotalMemoryFormattedString()

        FileUtils.copyAssets(this)
        val settings = getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE)

        val factory = SplashViewModelFactory(settings)
        splashViewModel = ViewModelProvider(this, factory)[SplashViewModel::class.java]

        val hasAutoSyncFeature = Constants.autoSynFeature(Constants.KEY_AUTOSYNC_, applicationContext)
        splashViewModel.setSettingsState(hasAutoSyncFeature)

        observeViewModel()

        getStarted.setOnClickListener {
            settings.edit().putBoolean(Constants.KEY_IS_CHILD, rbChild.isChecked).apply()
            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            splashViewModel.settingsState
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { state ->
                    state?.let {
                        when (it) {
                            SettingsState.ToDashboard -> {
                                navigateToDashboardActivity()
                            }
                            SettingsState.ToLogin -> {
                                navigateToLoginActivity()
                            }
                        }
                    }
                }
        }
    }

    private fun navigateToLoginActivity() {
        val loginIntent = Intent(this@SplashActivity, LoginActivity::class.java)
        startActivity(loginIntent)
        finish()
    }

    private fun navigateToDashboardActivity() {
        val dashboardIntent =
            Intent(
                this@SplashActivity,
                DashboardActivity::class.java
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(dashboardIntent)
        finish()
    }
}