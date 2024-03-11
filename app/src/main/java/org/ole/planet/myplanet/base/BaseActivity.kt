package org.ole.planet.myplanet.base

import android.R
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {
    public override fun onCreate(savedInstanceStat: Bundle?) {
        super.onCreate(savedInstanceStat)
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