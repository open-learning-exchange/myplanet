package org.ole.planet.myplanet.ui

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.sync.SyncActivity.Companion.clearRealmDb
import org.ole.planet.myplanet.ui.sync.SyncActivity.Companion.clearSharedPref
import org.ole.planet.myplanet.ui.sync.SyncActivity.Companion.restartApp
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils
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
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportFragmentManager.beginTransaction().replace(android.R.id.content, SettingFragment()).commit()
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
            startActivity(Intent(this, DashboardActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    class SettingFragment : PreferenceFragmentCompat() {
        lateinit var profileDbHandler: UserProfileDbHandler
        var user: RealmUserModel? = null
        private lateinit var dialog: DialogUtils.CustomProgressDialog

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val view = super.onCreateView(inflater, container, savedInstanceState)
            view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.secondary_bg))
            return view
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            requireContext().setTheme(R.style.PreferencesTheme)
            setPreferencesFromResource(R.xml.pref, rootKey)
            profileDbHandler = UserProfileDbHandler(requireActivity())
            user = profileDbHandler.userModel
            dialog = DialogUtils.getCustomProgressDialog(requireActivity())
            setBetaToggleOn()
            setAutoSyncToggleOn()
            setDownloadSyncFilesToggle()
            val lp = findPreference<ListPreference>("app_language")
            if (lp != null) {
                lp.onPreferenceChangeListener = OnPreferenceChangeListener { _: Preference?, o: Any ->
                    LocaleHelper.setLocale(requireActivity(), o.toString())
                    requireActivity().recreate()
                    true
                }
            }

            val darkMode = findPreference<Preference>("dark_mode")
            if (darkMode != null) {
                darkMode.onPreferenceChangeListener = OnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
                    if (preference?.key == "dark_mode") {
                        darkMode(newValue.toString())
                        return@OnPreferenceChangeListener true
                    }
                    false
                }
            }

            // Show Available space under the "Freeup Space" preference.
            val spacePreference = findPreference<Preference>("freeup_space")
            if (spacePreference != null) {
                spacePreference.summary = availableOverTotalMemoryFormattedString
            }
            clearDataButtonInit()
        }

        private fun clearDataButtonInit() {
            val mRealm = DatabaseService(requireActivity()).realmInstance
            val preference = findPreference<Preference>("reset_app")
            if (preference != null) {
                preference.onPreferenceClickListener = OnPreferenceClickListener {
                    AlertDialog.Builder(requireActivity()).setTitle(R.string.are_you_sure)
                        .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                            clearRealmDb()
                            clearSharedPref()
                            restartApp()
                        }.setNegativeButton(R.string.no, null).show()
                    false
                }
            }
            val prefFreeUp = findPreference<Preference>("freeup_space")
            if (prefFreeUp != null) {
                prefFreeUp.onPreferenceClickListener = OnPreferenceClickListener {
                    AlertDialog.Builder(requireActivity()).setTitle(R.string.are_you_sure_want_to_delete_all_the_files)
                        .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                            mRealm.executeTransactionAsync({ realm: Realm ->
                                val libraries = realm.where(RealmMyLibrary::class.java).findAll()
                                for (library in libraries) library.resourceOffline = false }, {
                                val f = File(Utilities.SD_PATH)
                                deleteRecursive(f)
                                Utilities.toast(requireActivity(), R.string.data_cleared.toString()) }) {
                                Utilities.toast(requireActivity(), R.string.unable_to_clear_files.toString())
                            }
                        }.setNegativeButton("No", null).show()
                    false
                }
            }
        }

        private fun deleteRecursive(fileOrDirectory: File) {
            if (fileOrDirectory.isDirectory) for (child in fileOrDirectory.listFiles()!!) deleteRecursive(child)
            fileOrDirectory.delete()
        }

        private fun setBetaToggleOn() {
            val beta = findPreference<SwitchPreference>("beta_function")
            val course = findPreference<SwitchPreference>("beta_course")
//            val rating = findPreference<SwitchPreference>("beta_rating")
//            val myHealth = findPreference<SwitchPreference>("beta_myHealth")
//            val healthWorker = findPreference<SwitchPreference>("beta_healthWorker")
//            val newsAddImage = findPreference<SwitchPreference>("beta_addImageToMessage")

            if (beta != null) {
                beta.onPreferenceChangeListener = OnPreferenceChangeListener { _: Preference?, _: Any? ->
                    if (beta.isChecked) {
                        if (course != null) {
                            course.isChecked = true
                        }
                    }
                    true
                }
            }
        }

        private fun setAutoSyncToggleOn() {
            val autoSync = findPreference<SwitchPreference>("auto_sync_with_server")
            val autoForceWeeklySync = findPreference<SwitchPreference>("force_weekly_sync")
            val autoForceMonthlySync = findPreference<SwitchPreference>("force_monthly_sync")
            val lastSyncDate = findPreference<Preference>("lastSyncDate")
            autoSync!!.onPreferenceChangeListener = OnPreferenceChangeListener { _: Preference?, _: Any? ->
                if (autoSync.isChecked) {
                    if (autoForceWeeklySync!!.isChecked) {
                        autoForceMonthlySync!!.isChecked = false
                    } else autoForceWeeklySync.isChecked = !autoForceMonthlySync!!.isChecked
                }
                true
            }
            autoForceSync(autoSync, autoForceWeeklySync!!, autoForceMonthlySync!!)
            autoForceSync(autoSync, autoForceMonthlySync, autoForceWeeklySync)
            val settings = requireActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val lastSynced = settings.getLong("LastSync", 0)
            if (lastSynced == 0L) {
                lastSyncDate?.setTitle(R.string.last_synced_never)
            } else if (lastSyncDate != null) {
                lastSyncDate.title = getString(R.string.last_synced_colon) + Utilities.getRelativeTime(lastSynced)
            }
        }

        private fun setDownloadSyncFilesToggle() {
            val downloadSyncFiles = findPreference<SwitchPreference>("download_sync_files")
            downloadSyncFiles?.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
                val isEnabled = newValue as Boolean
                val sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                sharedPreferences.edit().putBoolean("download_sync_files", isEnabled).apply()
                true
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            if (this::profileDbHandler.isInitialized) {
                profileDbHandler.onDestroy()
            }
        }

        private fun darkMode(key: String) {
            when (key) {
                "ON" ->  AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                "OFF" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "Follow System" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    companion object {
        var openDashboard = true
        private fun autoForceSync(autoSync: SwitchPreference, autoForceA: SwitchPreference, autoForceB: SwitchPreference) {
            autoForceA.onPreferenceChangeListener = OnPreferenceChangeListener { _: Preference?, _: Any? ->
                autoForceB.isChecked = !autoSync.isChecked
                true
            }
        }
    }
}