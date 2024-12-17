package org.ole.planet.myplanet.ui.sync

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.PorterDuff
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.realm.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.model.RealmUserChallengeActions.Companion.createAction
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.SettingActivity
import org.ole.planet.myplanet.ui.community.CommunityTabFragment
import org.ole.planet.myplanet.ui.courses.CoursesFragment
import org.ole.planet.myplanet.ui.dashboard.BellDashboardFragment
import org.ole.planet.myplanet.ui.dashboard.DashboardFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.rating.RatingFragment.Companion.newInstance
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.team.TeamFragment
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.Utilities

abstract class DashboardElementActivity : SyncActivity(), FragmentManager.OnBackStackChangedListener {
    lateinit var navigationView: BottomNavigationView
    var doubleBackToExitPressedOnce = false
    private lateinit var goOnline: MenuItem
    var c = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileDbHandler = UserProfileDbHandler(this)
        settings = applicationContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefData = SharedPrefManager(this)
    }

    fun onClickTabItems(position: Int) {
        when (position) {
            0 -> openCallFragment(BellDashboardFragment(), "dashboard")
            1 -> openCallFragment(ResourcesFragment(), "library")
            2 -> openCallFragment(CoursesFragment(), "course")
            4 -> openEnterpriseFragment()
            3 -> openCallFragment(TeamFragment(), "survey")
            5 -> {
                openCallFragment(CommunityTabFragment(), "community")
            }
        }
    }

    private fun showGuestUserDialog() {
        AlertDialog.Builder(this)
            .setTitle("Access Restricted")
            .setMessage("You need to be logged in with a full account to access the community features.")
            .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_dashboard, menu)
        goOnline = menu.findItem(R.id.menu_goOnline)
        return true
    }

    fun openCallFragment(newFragment: Fragment, tag: String?) {
        val fragmentManager = supportFragmentManager
        if(c<2){
            c=0
        }
        val existingFragment = fragmentManager.findFragmentByTag(tag)
        if (tag == "") {
            c++
            if(c>2){
                c--
                fragmentManager.popBackStack(tag, 0)
            }else{
                fragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, newFragment, tag)
                    .addToBackStack(tag)
                    .commit()
            }
        } else {
            if (existingFragment != null && existingFragment.isVisible) {
                return
            } else if (existingFragment != null) {
                if(c>0 && c>2){
                    c=0
                }
                fragmentManager.popBackStack(tag, 0)
            } else {
                if(c>0 && c>2){
                    c=0
                }
                if(tag!="") {
                    fragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, newFragment, tag)
                        .addToBackStack(tag)
                        .commit()
                }
            }
        }
    }
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        goOnline.isVisible = showBetaFeature(Constants.KEY_SYNC, this)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_goOnline -> {
                wifiStatusSwitch()
                return true
            }
            R.id.menu_logout -> {
                logout()
            }
            R.id.action_feedback -> {
                openCallFragment(FeedbackFragment(), getString(R.string.menu_feedback))
            }
            R.id.action_setting -> {
                startActivity(Intent(this, SettingActivity::class.java))
            }
            R.id.action_sync -> {
                logSyncInSharedPrefs()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun logSyncInSharedPrefs() {
        val url = Utilities.getUrl()
        val regex = Regex("^(https?://).*?@(.*?):")
        val matchResult = regex.find(url)

        val extractedUrl = if (matchResult != null) {
            val protocol = matchResult.groupValues[1]
            val address = matchResult.groupValues[2]
            "$protocol$address"
        } else {
            null
        }

        val serverMappings = mapOf(
            "http://${BuildConfig.PLANET_URIUR_URL}" to "http://35.231.161.29",
            "http://192.168.1.202" to "http://34.35.29.147",
            "https://${BuildConfig.PLANET_GUATEMALA_URL}" to "http://guatemala.com/cloudserver",
            "http://${BuildConfig.PLANET_XELA_URL}" to "http://xela.com/cloudserver",
            "http://${BuildConfig.PLANET_SANPABLO_URL}" to "http://sanpablo.com/cloudserver",
            "http://${BuildConfig.PLANET_EMBAKASI_URL}" to "http://embakasi.com/cloudserver",
            "https://${BuildConfig.PLANET_VI_URL}" to "http://vi.com/cloudserver"
        )

        lifecycleScope.launch {
            Log.d("URLSync", "Original URL being processed: $url")
            Log.d("URLSync", "Extracted base URL: $extractedUrl")

            // Find the first matching URL in serverMappings
            val primaryUrlMapping = serverMappings.entries.find { it.key.contains(extractedUrl ?: "") }

            if (primaryUrlMapping != null) {
                val primaryUrl = primaryUrlMapping.key
                val alternativeUrl = primaryUrlMapping.value

                // Log the mapped URLs
                Log.d("URLSync", "Mapped Primary URL: $primaryUrl")
                Log.d("URLSync", "Mapped Alternative URL: $alternativeUrl")

                // Check primary URL first
                Log.d("URLSync", "Attempting to reach primary URL: ${Utilities.getUrl()}")
                val isPrimaryReachable = isServerReachable(Utilities.getUrl())

                if (isPrimaryReachable) {
                    Log.d("URLSync", "Successfully reached primary URL: ${Utilities.getUrl()}")
//                    processUrlAndStartUpload(Utilities.getUrl())
                    startUpload("dashboard")
                    createAction(mRealm, "${profileDbHandler.userModel?.id}", null, "sync")
                } else {
                    Log.w("URLSync", "Failed to reach primary URL: ${Utilities.getUrl()}")

                    val uri = Uri.parse(alternativeUrl)
                    var couchdbURL: String
                    val urlUser: String
                    val urlPwd: String
                    if (alternativeUrl.contains("@")) {
                        val userinfo = getUserInfo(uri)
                        urlUser = userinfo[0]
                        urlPwd = userinfo[1]
                        couchdbURL = alternativeUrl
                    } else {
                        urlUser = "satellite"
                        urlPwd = settings.getString("serverPin", "") ?: ""
                        couchdbURL = "${uri.scheme}://$urlUser:$urlPwd@${uri.host}:${if (uri.port == -1) (if (uri.scheme == "http") 80 else 443) else uri.port}"
                    }

                    // If primary URL is not reachable, try alternative URL
                    Log.d("URLSync", "Attempting to reach alternative URL: $couchdbURL")
                    editor.putString("url_user", urlUser)
                    editor.putString("url_pwd", urlPwd)
                    editor.putString("url_Scheme", uri.scheme)
                    editor.putString("url_Host", uri.host)
                    editor.putString("alternativeUrl", url)
                    editor.putString("processedAlternativeUrl", couchdbURL)
                    editor.putBoolean("isAlternativeUrl", true)
                    editor.apply()
                    val isAlternativeReachable = isServerReachable(couchdbURL)

                    if (isAlternativeReachable) {
                        startUpload("dashboard")
                        createAction(mRealm, "${profileDbHandler.userModel?.id}", null, "sync")
                    } else {
                        // Neither primary nor alternative URL is reachable
                        Log.e("URLSync", "Both primary and alternative URLs are unreachable")
                        Log.e("URLSync", "Primary URL failed: $primaryUrl")
                        Log.e("URLSync", "Alternative URL failed: $alternativeUrl")
                    }
                }
            } else {
                // If no mapping found, log and proceed with original URL
                Log.w("URLSync", "No URL mapping found for: $extractedUrl")
                Log.d("URLSync", "Proceeding with original URL")
//                processUrlAndStartUpload(url)
                startUpload("dashboard")
                createAction(mRealm, "${profileDbHandler.userModel?.id}", null, "sync")
            }
        }
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
            Handler(Looper.getMainLooper()).postDelayed({ connectToWifi() }, 5000)
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
        CoroutineScope(Dispatchers.Main).launch {
            profileDbHandler.onLogout()
            settings.edit().putBoolean(Constants.KEY_LOGIN, false).apply()
            settings.edit().putBoolean(Constants.KEY_NOTIFICATION_SHOWN, false).apply()
            val loginScreen = Intent(this@DashboardElementActivity, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
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
            Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
        }
    }

    fun showRatingDialog(type: String?, resourceId: String?, title: String?, listener: OnRatingChangeListener?) {
        val f = newInstance(type, resourceId, title)
        f.setListener(listener)
        f.show(supportFragmentManager, "")
    }

    override fun onBackStackChanged() {
        val f = supportFragmentManager.findFragmentById(R.id.fragment_container)
        val fragmentTag = f?.tag
        if (f is CoursesFragment) {
            if ("shelf" == fragmentTag) {
                navigationView.menu.findItem(R.id.menu_mycourses).isChecked = true
            } else {
                navigationView.menu.findItem(R.id.menu_courses).isChecked = true
            }
        } else if (f is ResourcesFragment) {
            if ("shelf" == fragmentTag) {
                navigationView.menu.findItem(R.id.menu_mylibrary).isChecked = true
            } else {
                navigationView.menu.findItem(R.id.menu_library).isChecked = true
            }
        } else if (f is DashboardFragment) {
            navigationView.menu.findItem(R.id.menu_home).isChecked = true
        }
    }

    fun openEnterpriseFragment() {
        val fragment: Fragment = TeamFragment()
        val b = Bundle()
        b.putString("type", "enterprise")
        fragment.arguments = b
        openCallFragment(fragment, "Enterprise")
    }
}
