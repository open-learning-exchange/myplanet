package org.ole.planet.myplanet.base

import android.R
import android.os.Bundle
import android.view.MenuItem
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import org.ole.planet.myplanet.utilities.LocaleHelper

abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    fun initActionBar() {
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}