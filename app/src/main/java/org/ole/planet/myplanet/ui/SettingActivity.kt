package org.ole.planet.myplanet.ui

import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.preference.ListPreference
import android.preference.Preference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.Preference.OnPreferenceClickListener
import android.preference.PreferenceFragment
import android.preference.SwitchPreference
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.dashboard.DashboardFragment
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utilities.FileUtils.availableOverTotalMemoryFormattedString
import org.ole.planet.myplanet.utilities.LocaleHelper
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File

class SettingActivity : AppCompatActivity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        fragmentManager.beginTransaction().replace(android.R.id.content, SettingFragment()).commit()
        title = getString(R.string.action_settings)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun finish() {
        super.finish()
        if (openDashboard) {
            startActivity(
                Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    class SettingFragment : PreferenceFragment() {
        var profileDbHandler: UserProfileDbHandler? = null
        var user: RealmUserModel? = null
        var dialog: ProgressDialog? = null
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.pref)
            profileDbHandler = UserProfileDbHandler(activity)
            user = profileDbHandler!!.userModel
            dialog = ProgressDialog(activity)
            setBetaToggleOn()
            setAutoSyncToggleOn()
            val lp = findPreference("app_language") as ListPreference
            lp.onPreferenceChangeListener = OnPreferenceChangeListener { _: Preference?, o: Any ->
                LocaleHelper.setLocale(activity, o.toString())
                activity.recreate()
                true
            }

            // Show Available space under the "Freeup Space" preference.
            val spacePreference = findPreference("freeup_space")
            spacePreference.summary = availableOverTotalMemoryFormattedString
            clearDataButtonInit()
        }

        private fun clearDataButtonInit() {
            val mRealm = DatabaseService(activity).realmInstance
            val preference = findPreference("reset_app")
            preference.onPreferenceClickListener = OnPreferenceClickListener {
                AlertDialog.Builder(activity).setTitle(R.string.are_you_sure)
                    .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                        BaseResourceFragment.settings.edit().clear().apply()
                        mRealm.executeTransactionAsync(
                            Realm.Transaction { realm: Realm -> realm.deleteAll() },
                            Realm.Transaction.OnSuccess {
                                Utilities.toast(activity, R.string.data_cleared.toString())
                                startActivity(Intent(activity, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
                                openDashboard = false
                                activity.finish()
                            })
                    }.setNegativeButton(R.string.no, null).show()
                false
            }
            val pref_freeup = findPreference("freeup_space")
            pref_freeup.onPreferenceClickListener = OnPreferenceClickListener {
                AlertDialog.Builder(activity).setTitle(R.string.are_you_sure_want_to_delete_all_the_files)
                    .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                        mRealm.executeTransactionAsync({ realm: Realm ->
                            val libraries = realm.where(RealmMyLibrary::class.java).findAll()
                            for (library in libraries) library.resourceOffline = false }, {
                                val f = File(Utilities.SD_PATH)
                            deleteRecursive(f)
                            Utilities.toast(activity, R.string.data_cleared.toString()) }) {
                            error: Throwable? -> Utilities.toast(activity, R.string.unable_to_clear_files.toString())
                        }
                    }.setNegativeButton("No", null).show()
                false
            }
        }

        fun deleteRecursive(fileOrDirectory: File) {
            if (fileOrDirectory.isDirectory) for (child in fileOrDirectory.listFiles()!!) deleteRecursive(child)
            fileOrDirectory.delete()
        }

        fun setBetaToggleOn() {
            val beta = findPreference("beta_function") as SwitchPreference
            val course = findPreference("beta_course") as SwitchPreference
            val achievement = findPreference("beta_achievement") as SwitchPreference
            val rating = findPreference("beta_rating") as SwitchPreference
            val myHealth = findPreference("beta_myHealth") as SwitchPreference
            val healthWorker = findPreference("beta_healthWorker") as SwitchPreference
            val newsAddImage = findPreference("beta_addImageToMessage") as SwitchPreference
            beta.onPreferenceChangeListener = OnPreferenceChangeListener { _: Preference?, _: Any? ->
                if (beta.isChecked) {
                    course.isChecked = true
                    achievement.isChecked = true
                }
                true
            }
        }

        fun setAutoSyncToggleOn() {
            val autoSync = findPreference("auto_sync_with_server") as SwitchPreference
            val autoForceWeeklySync = findPreference("force_weekly_sync") as SwitchPreference
            val autoForceMonthlySync = findPreference("force_monthly_sync") as SwitchPreference
            val lastSyncDate = findPreference("lastSyncDate") as Preference
            autoSync.onPreferenceChangeListener = OnPreferenceChangeListener { _: Preference?, _: Any? ->
                if (autoSync.isChecked) {
                    if (autoForceWeeklySync.isChecked) {
                        autoForceMonthlySync.isChecked = false
                    } else if (autoForceMonthlySync.isChecked) {
                        autoForceWeeklySync.isChecked = false
                    } else {
                        autoForceWeeklySync.isChecked = true
                    }
                }
                true
            }
            autoForceSync(autoSync, autoForceWeeklySync, autoForceMonthlySync)
            autoForceSync(autoSync, autoForceMonthlySync, autoForceWeeklySync)
            val settings = activity.getSharedPreferences(DashboardFragment.PREFS_NAME, MODE_PRIVATE)
            val lastSynced = settings.getLong("LastSync", 0)
            if (lastSynced == 0L) {
                lastSyncDate.setTitle(R.string.last_synced_never)
            } else lastSyncDate.title = getString(R.string.last_synced_colon) + Utilities.getRelativeTime(lastSynced)
        }

        override fun onDestroy() {
            super.onDestroy()
            profileDbHandler!!.onDestory()
        }
    }

    companion object {
        var openDashboard = true
        private fun autoForceSync(autoSync: SwitchPreference, autoForceA: SwitchPreference, autoForceB: SwitchPreference) {
            autoForceA.onPreferenceChangeListener = OnPreferenceChangeListener { _: Preference?, _: Any? ->
                if (autoSync.isChecked) {
                    autoForceB.isChecked = false
                } else {
                    autoForceB.isChecked = true
                }
                true
            }
        }
    }
}
