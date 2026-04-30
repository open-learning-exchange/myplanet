package org.ole.planet.myplanet.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.DialogServerUrlBinding
import org.ole.planet.myplanet.model.RealmUserChallengeActions.Companion.createActionAsync
import org.ole.planet.myplanet.ui.community.CommunityTabFragment
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.ole.planet.myplanet.ui.courses.CoursesFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.ratings.RatingsFragment.Companion.newInstance
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.ui.teams.TeamFragment
import org.ole.planet.myplanet.utils.NotificationUtils
import org.ole.planet.myplanet.utils.SecurePrefs
import org.ole.planet.myplanet.data.DatabaseService
import javax.inject.Inject

abstract class DashboardElementActivity : SyncActivity(), FragmentManager.OnBackStackChangedListener {
    @Inject
    lateinit var databaseService: DatabaseService
    lateinit var navigationView: BottomNavigationView
    private lateinit var goOnline: MenuItem
    var c = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.addOnBackStackChangedListener(this)
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
                FragmentNavigator.popBackStack(fragmentManager, tag, 0)
            }else{
                FragmentNavigator.replaceFragment(
                    fragmentManager,
                    R.id.fragment_container,
                    newFragment,
                    addToBackStack = true,
                    tag = tag
                )
            }
        } else {
            if (existingFragment != null && existingFragment.isVisible) {
                return
            } else if (existingFragment != null) {
                if(c>0 && c>2){
                    c=0
                }
                FragmentNavigator.popBackStack(fragmentManager, tag, 0)
            } else {
                if(c>0 && c>2){
                    c=0
                }
                if(tag!="") {
                    FragmentNavigator.replaceFragment(
                        fragmentManager,
                        R.id.fragment_container,
                        newFragment,
                        addToBackStack = true,
                        tag = tag
                    )
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_logout -> {
                logout()
            }
            R.id.action_feedback -> {
                openCallFragment(FeedbackFragment(), getString(R.string.menu_feedback))
            }
            R.id.action_sync -> {
                logSyncInSharedPrefs()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun logSyncInSharedPrefs() {
        val protocol = prefData.getServerProtocol()
        val serverUrl = prefData.getServerUrl()
        val serverPin = prefData.getServerPin()

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
        checkMinApk(url, serverPin, "DashboardActivity")
        lifecycleScope.launch {
            val userModel = profileDbHandler.getUserModel()
            createActionAsync(databaseService, "${userModel?.id}", null, "sync")
        }
    }

    fun logout() {
        lifecycleScope.launch {
            profileDbHandler.logoutAsync()
            withContext(Dispatchers.IO) { SecurePrefs.clearCredentials(this@DashboardElementActivity) }
            prefData.setLoggedIn(false)
            prefData.setNotificationShown(false)
            NotificationUtils.cancelAll(this@DashboardElementActivity)
            
            val loginScreen = Intent(this@DashboardElementActivity, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("fromLogout", true)
            startActivity(loginScreen)
            finish()
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
            if ("MyCoursesFragment" == fragmentTag) {
                navigationView.menu.findItem(R.id.menu_mycourses).isChecked = true
            } else {
                navigationView.menu.findItem(R.id.menu_courses).isChecked = true
            }
        } else if (f is ResourcesFragment) {
            if ("MyResourcesFragment" == fragmentTag) {
                navigationView.menu.findItem(R.id.menu_mylibrary).isChecked = true
            } else {
                navigationView.menu.findItem(R.id.menu_library).isChecked = true
            }
        }
    }

    fun openEnterpriseFragment() {
        val fragment: Fragment = TeamFragment()
        val b = Bundle()
        b.putString("type", "enterprise")
        fragment.arguments = b
        openCallFragment(fragment, "Enterprise")
    }

    override fun onDestroy() {
        supportFragmentManager.removeOnBackStackChangedListener(this)
        super.onDestroy()
    }
}
