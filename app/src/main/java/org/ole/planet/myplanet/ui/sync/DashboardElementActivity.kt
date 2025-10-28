package org.ole.planet.myplanet.ui.sync

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PorterDuff
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.DialogServerUrlBinding
import org.ole.planet.myplanet.model.RealmUserChallengeActions.Companion.createActionAsync
import org.ole.planet.myplanet.ui.navigation.DashboardDestination
import org.ole.planet.myplanet.ui.navigation.DashboardNavigator
import org.ole.planet.myplanet.ui.rating.RatingFragment.Companion.newInstance
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Constants.isBetaWifiFeatureEnabled
import org.ole.planet.myplanet.utilities.NotificationUtils
import org.ole.planet.myplanet.utilities.SecurePrefs
import org.ole.planet.myplanet.utilities.SharedPrefManager

abstract class DashboardElementActivity : SyncActivity(), FragmentManager.OnBackStackChangedListener {
    lateinit var navigationView: BottomNavigationView
    var doubleBackToExitPressedOnce = false
    private lateinit var goOnline: MenuItem
    protected lateinit var dashboardNavigator: DashboardNavigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = applicationContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefData = SharedPrefManager(this)
        supportFragmentManager.addOnBackStackChangedListener(this)
    }

    fun onClickTabItems(position: Int) {
        val destination = when (position) {
            0 -> DashboardDestination.Home
            1 -> DashboardDestination.Library
            2 -> DashboardDestination.Courses
            3 -> DashboardDestination.Team()
            4 -> DashboardDestination.Team(type = "enterprise")
            5 -> DashboardDestination.Community
            else -> null
        }
        destination?.let { openCallFragment(it) }
    }

    protected fun setupDashboardNavigator(
        bottomNavigationView: BottomNavigationView? = null,
        drawerSelectionListener: ((Long?) -> Unit)? = null
    ) {
        dashboardNavigator = DashboardNavigator(
            fragmentManager = supportFragmentManager,
            containerId = R.id.fragment_container,
            bottomNavigationView = bottomNavigationView,
            drawerSelectionListener = drawerSelectionListener
        )
    }

    private fun ensureNavigator(): DashboardNavigator {
        if (!::dashboardNavigator.isInitialized) {
            dashboardNavigator = DashboardNavigator(
                fragmentManager = supportFragmentManager,
                containerId = R.id.fragment_container
            )
        }
        return dashboardNavigator
    }

    protected fun bindGoOnlineMenu(menu: Menu) {
        goOnline = menu.findItem(R.id.menu_goOnline)
        updateGoOnlineVisibility()
    }

    fun openCallFragment(destination: DashboardDestination) {
        ensureNavigator().navigate(destination)
    }
    protected fun updateGoOnlineVisibility() {
        if (::goOnline.isInitialized) {
            goOnline.isVisible = isBetaWifiFeatureEnabled(this)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_goOnline -> {
                wifiStatusSwitch()
                return true
            }
            R.id.action_logout -> {
                logout()
            }
            R.id.action_feedback -> {
                openCallFragment(DashboardDestination.Feedback)
            }
            R.id.action_sync -> {
                logSyncInSharedPrefs()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun logSyncInSharedPrefs() {
        val protocol = settings.getString("serverProtocol", "")
        val serverUrl = "${settings.getString("serverURL", "")}"
        val serverPin = "${settings.getString("serverPin", "")}"

        val url = if (serverUrl.startsWith("http://") || serverUrl.startsWith("https://")) {
            serverUrl
        } else {
            "$protocol$serverUrl"
        }

        val dialogServerUrlBinding = DialogServerUrlBinding.inflate(LayoutInflater.from(this))
        val contextWrapper = ContextThemeWrapper(this, R.style.AlertDialogTheme)

        val builder = MaterialDialog.Builder(contextWrapper)
            .customView(dialogServerUrlBinding.root, true)

        val dialog = builder.build()
        currentDialog = dialog
        service.getMinApk(this, url, serverPin, this, "DashboardActivity")
        createActionAsync(mRealm, "${profileDbHandler.userModel?.id}", null, "sync")
    }

    @SuppressLint("RestrictedApi")
    fun wifiStatusSwitch() {
        val resIcon = ContextCompat.getDrawable(this, R.drawable.goonline)
        val connManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        startActivity(intent)
        if (mWifi?.isConnected == true) {
            wifi.isWifiEnabled = false
            if (resIcon != null) {
                DrawableCompat.setTintMode(resIcon.mutate(), PorterDuff.Mode.SRC_ATOP)
                DrawableCompat.setTint(resIcon, ContextCompat.getColor(this, R.color.green))
            }
            goOnline.icon = resIcon
            Toast.makeText(this, getString(R.string.wifi_is_turned_off_saving_battery_power), Toast.LENGTH_LONG).show()
        } else {
            wifi.isWifiEnabled = true
            Toast.makeText(this, getString(R.string.turning_on_wifi_please_wait), Toast.LENGTH_LONG).show()
            lifecycleScope.launch {
                delay(5000)
                connectToWifi()
            }
            if (resIcon != null) {
                DrawableCompat.setTintMode(resIcon.mutate(), PorterDuff.Mode.SRC_ATOP)
                DrawableCompat.setTint(resIcon, ContextCompat.getColor(this, R.color.accent))
            }
            goOnline.icon = resIcon
        }
    }

    private fun connectToWifi() {
        val id = settings.getInt("LastWifiID", -1)
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val netId: Int
        for (tmp in wifiManager.configuredNetworks) {
            if (tmp.networkId > -1 && tmp.networkId == id) {
                netId = tmp.networkId
                wifiManager.enableNetwork(netId, true)
                Toast.makeText(this, R.string.you_are_now_connected + netId, Toast.LENGTH_SHORT).show()
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("ACTION_NETWORK_CHANGED"))
                break
            }
        }
    }

    fun logout() {
        lifecycleScope.launch {
            profileDbHandler.logoutAsync()
            SecurePrefs.clearCredentials(this@DashboardElementActivity)
            settings.edit { putBoolean(Constants.KEY_LOGIN, false) }
            settings.edit { putBoolean(Constants.KEY_NOTIFICATION_SHOWN, false) }
            NotificationUtils.cancelAll(this@DashboardElementActivity)
            
            val loginScreen = Intent(this@DashboardElementActivity, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("fromLogout", true)
            startActivity(loginScreen)
            doubleBackToExitPressedOnce = true
            finish()
        }
    }

    override fun finish() {
        if (doubleBackToExitPressedOnce) {
            super.finish()
        } else {
            doubleBackToExitPressedOnce = true
            Toast.makeText(this, getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                delay(2000)
                doubleBackToExitPressedOnce = false
            }
        }
    }

    fun showRatingDialog(type: String?, resourceId: String?, title: String?, listener: OnRatingChangeListener?) {
        val f = newInstance(type, resourceId, title)
        f.setListener(listener)
        f.show(supportFragmentManager, "")
    }

    override fun onBackStackChanged() {
        ensureNavigator().handleBackStackChanged()
    }

    fun openEnterpriseFragment() {
        openCallFragment(DashboardDestination.Team(type = "enterprise"))
    }
}
