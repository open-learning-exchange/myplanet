package org.ole.planet.myplanet.ui

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.di.DefaultPreferences
import org.ole.planet.myplanet.datamanager.DatabaseService
import javax.inject.Inject
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseResourceFragment.Companion.backgroundDownload
import org.ole.planet.myplanet.base.BaseResourceFragment.Companion.getAllLibraryList
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.sync.SyncActivity.Companion.clearRealmDb
import org.ole.planet.myplanet.ui.sync.SyncActivity.Companion.clearSharedPref
import org.ole.planet.myplanet.ui.sync.SyncActivity.Companion.restartApp
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.DownloadUtils.downloadAllFiles
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtil
import org.ole.planet.myplanet.utilities.FileUtils.availableOverTotalMemoryFormattedString
import org.ole.planet.myplanet.utilities.LocaleHelper
import org.ole.planet.myplanet.utilities.ThemeManager
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class SettingActivity : AppCompatActivity() {

    @Inject
    lateinit var databaseService: DatabaseService

    @Inject
    @AppPreferences
    lateinit var appPreferences: SharedPreferences

    @Inject
    @DefaultPreferences 
    lateinit var defaultPreferences: SharedPreferences

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportFragmentManager.beginTransaction().replace(android.R.id.content, SettingFragment()).commit()
        EdgeToEdgeUtil.setupEdgeToEdge(this, findViewById(android.R.id.content))
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

    @AndroidEntryPoint
    class SettingFragment : PreferenceFragmentCompat() {
        @Inject
        lateinit var profileDbHandler: UserProfileDbHandler
        var user: RealmUserModel? = null
        private lateinit var dialog: DialogUtils.CustomProgressDialog
        private lateinit var defaultPref: SharedPreferences
        lateinit var settings: SharedPreferences

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val view = super.onCreateView(inflater, container, savedInstanceState)
            view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.secondary_bg))
            return view
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            requireContext().setTheme(R.style.PreferencesTheme)
            setPreferencesFromResource(R.xml.pref, rootKey)
            user = profileDbHandler.userModel
            dialog = DialogUtils.getCustomProgressDialog(requireActivity())
            defaultPref = (requireActivity() as SettingActivity).defaultPreferences
            settings = (requireActivity() as SettingActivity).appPreferences

            setBetaToggleOn()
            setAutoSyncToggleOn()
            val lp = findPreference<Preference>("app_language")
            lp?.setOnPreferenceClickListener {
                context?.let { it1 -> languageChanger(it1) }
                true
            }

            val darkMode = findPreference<Preference>("dark_mode")
            darkMode?.setOnPreferenceClickListener {
                ThemeManager.showThemeDialog(requireActivity())
                true
            }

            // Show Available space under the "Freeup Space" preference.
            val spacePreference = findPreference<Preference>("freeup_space")
            if (spacePreference != null) {
                spacePreference.summary = "${getString(R.string.available_space_colon)} $availableOverTotalMemoryFormattedString"
            }

            val autoDownload = findPreference<SwitchPreference>("beta_auto_download")
            autoDownload?.onPreferenceChangeListener = OnPreferenceChangeListener { _: Preference?, _: Any? ->
                if (autoDownload.isChecked == true) {
                    defaultPref.edit { putBoolean("beta_auto_download", true) }
                    backgroundDownload(downloadAllFiles(getAllLibraryList((requireActivity() as SettingActivity).databaseService.realmInstance)), requireContext())
                } else {
                    defaultPref.edit { putBoolean("beta_auto_download", false) }
                }
                true
            }

            val fastSync = findPreference<SwitchPreference>("beta_fast_sync")
            val isFastSync = settings.getBoolean("fastSync", false)
            fastSync?.isChecked = isFastSync
            fastSync?.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
                val isChecked = newValue as Boolean
                settings.edit { putBoolean("fastSync", isChecked) }
                true
            }

            clearDataButtonInit()
        }

        private fun clearDataButtonInit() {
            val mRealm = (requireActivity() as SettingActivity).databaseService.realmInstance
            val preference = findPreference<Preference>("reset_app")
            if (preference != null) {
                preference.onPreferenceClickListener = OnPreferenceClickListener {
                    AlertDialog.Builder(requireActivity()).setTitle(R.string.are_you_sure)
                        .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                            CoroutineScope(Dispatchers.Main).launch {
                                clearRealmDb()
                                clearSharedPref()
                                restartApp()
                            }
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

        override fun onDestroy() {
            super.onDestroy()
            if (this::profileDbHandler.isInitialized) {
                profileDbHandler.onDestroy()
            }
        }

        companion object {

            fun languageChanger(context: Context) {
                val options = arrayOf(
                    context.getString(R.string.english),
                    context.getString(R.string.spanish),
                    context.getString(R.string.somali),
                    context.getString(R.string.nepali),
                    context.getString(R.string.arabic),
                    context.getString(R.string.french)
                )
                val currentLanguage = LocaleHelper.getLanguage(context)
                val checkedItem = when (currentLanguage) {
                    "en" -> 0
                    "es" -> 1
                    "so" -> 2
                    "ne" -> 3
                    "ar" -> 4
                    "fr" -> 5
                    else -> 0
                }

                val builder = AlertDialog.Builder(context, R.style.AlertDialogTheme)
                    .setTitle(context.getString(R.string.select_language))
                    .setSingleChoiceItems(ArrayAdapter(context, R.layout.checked_list_item, options), checkedItem) { dialog, which ->
                        val selectedLanguage = when (which) {
                            0 -> "en"
                            1 -> "es"
                            2 -> "so"
                            3 -> "ne"
                            4 -> "ar"
                            5 -> "fr"
                            else -> "en"
                        }
                        LocaleHelper.setLocale(context, selectedLanguage)
                        (context as Activity).recreate()
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)

                val dialog = builder.create()
                dialog.show()
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
