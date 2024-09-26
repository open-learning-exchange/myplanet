package org.ole.planet.myplanet.base

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utilities.LocaleHelper
import java.util.Locale

abstract class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        val localeUpdatedContext = LocaleHelper.onAttach(newBase)
        super.attachBaseContext(localeUpdatedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resetTitle()
        updateConfigurationIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateConfigurationIfNeeded()
    }

    private fun resetTitle() {
        try {
            val label = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA).labelRes
            if (label != 0) {
                setTitle(label)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }

    private fun updateConfigurationIfNeeded() {
        val currentLanguage = LocaleHelper.getLanguage(this)
        val newConfig = resources.configuration
        val newLocale = Locale(currentLanguage)

        if (newConfig.locale != newLocale) {
            Locale.setDefault(newLocale)
            newConfig.setLocale(newLocale)
            newConfig.setLayoutDirection(newLocale)
            resources.updateConfiguration(newConfig, resources.displayMetrics)
            supportActionBar?.title = title
        }
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    fun initActionBar() {
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }
}
