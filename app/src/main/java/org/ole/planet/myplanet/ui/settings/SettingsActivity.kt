package org.ole.planet.myplanet.ui.settings

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.RetryOperation
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.retry.RetryQueueWorker
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.sync.SyncActivity.Companion.restartApp
import org.ole.planet.myplanet.utils.DialogUtils
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.LocaleUtils
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.TimeUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.collectLatestWhenStarted

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleUtils.onAttach(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeUtils.setupEdgeToEdge(this, findViewById(android.R.id.content), lightStatusBar = false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        FragmentNavigator.replaceFragment(supportFragmentManager, android.R.id.content, SettingFragment())
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
        private val viewModel: SettingsViewModel by viewModels()
        @Inject
        lateinit var profileDbHandler: UserSessionManager
        @Inject
        lateinit var sharedPrefManager: SharedPrefManager
        @Inject
        lateinit var timeProvider: TimeProvider
        var user: UserEntity? = null
        private var libraryList: List<MyLibrary>? = null


        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            collectLatestWhenStarted(viewModel.clearDataEvent) {
                restartApp()
            }
            collectLatestWhenStarted(viewModel.downloadCompleteEvent) { files ->
                libraryList = files
                val autoDownload = findPreference<SwitchPreference>("beta_auto_download")
                autoDownload?.isEnabled = true
            }
            collectLatestWhenStarted(viewModel.clearRetryQueueEvent) { cleared ->
                if (cleared) {
                    Utilities.toast(requireActivity(), getString(R.string.retry_queue_cleared))
                } else {
                    Utilities.toast(requireActivity(), "Cannot clear while processing")
                }
            }
            collectLatestWhenStarted(viewModel.retryQueueDetailsEvent) { detailsData ->
                val pendingCount = detailsData.pendingCount
                val pendingOps = detailsData.pendingOps
                val isProcessing = detailsData.isProcessing

                val details = buildString {
                    if (isProcessing) {
                        appendLine("⏳ Currently processing retries...")
                        appendLine()
                    }
                    appendLine(getString(R.string.pending_retries, pendingCount.toInt()))
                    appendLine()
                    if (pendingOps.isNotEmpty()) {
                        appendLine("Details:")
                        pendingOps.take(10).forEach { op ->
                            val statusIcon = when (op.status) {
                                RetryOperation.STATUS_IN_PROGRESS -> "🔄"
                                RetryOperation.STATUS_PENDING -> "⏸"
                                else -> "❓"
                            }
                            appendLine("$statusIcon ${op.uploadType}: ${op.status} (${op.attemptCount}/${op.maxAttempts})")
                        }
                        if (pendingOps.size > 10) {
                            appendLine("... and ${pendingOps.size - 10} more")
                        }
                    } else {
                        appendLine("No pending operations")
                    }
                }

                val retryDialog = AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.retry_queue_status)
                    .setMessage(details)
                    .setPositiveButton(R.string.trigger_retry_now, null)
                    .setNegativeButton(R.string.clear_retry_queue, null)
                    .setNeutralButton(R.string.cancel, null)
                    .create()

                retryDialog.setOnShowListener {
                    val retryButton = retryDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    val clearButton = retryDialog.getButton(AlertDialog.BUTTON_NEGATIVE)

                    // Disable buttons if processing
                    retryButton.isEnabled = !isProcessing && pendingCount > 0
                    clearButton.isEnabled = !isProcessing && pendingCount > 0

                    retryButton.setOnClickListener {
                        if (!viewModel.isCurrentlyProcessing()) {
                            RetryQueueWorker.triggerImmediateRetry(requireContext())
                            Utilities.toast(requireActivity(), getString(R.string.retry_triggered))
                            retryDialog.dismiss()
                        } else {
                            Utilities.toast(requireActivity(), "Retry already in progress")
                        }
                    }

                    clearButton.setOnClickListener {
                        viewModel.clearRetryQueue()
                        retryDialog.dismiss()
                    }
                }

                retryDialog.show()
            }
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val view = super.onCreateView(inflater, container, savedInstanceState)
            view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.secondary_bg))
            return view
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            requireContext().setTheme(R.style.PreferencesTheme)
            setPreferencesFromResource(R.xml.pref, rootKey)
            lifecycleScope.launch {
                user = profileDbHandler.getUserModel()
                blockGuestSwitches()
            }

            setBetaToggleOn()
            setAutoSyncToggleOn()
            val lp = findPreference<Preference>("app_language")
            lp?.setOnPreferenceClickListener {
                context?.let { it1 -> languageChanger(it1) }
                true
            }

            val textSize = findPreference<Preference>("text_size")
            textSize?.setOnPreferenceClickListener {
                textSizeChanger(requireActivity())
                true
            }

            val autoDownload = findPreference<SwitchPreference>("beta_auto_download")
            autoDownload?.onPreferenceChangeListener = OnPreferenceChangeListener { preference, newValue ->
                val isChecked = newValue as Boolean
                if (isChecked) {
                    preference.isEnabled = false
                    sharedPrefManager.setBetaAutoDownload(true)
                    viewModel.downloadFiles(libraryList)
                } else {
                    sharedPrefManager.setBetaAutoDownload(false)
                }
                true
            }

            clearDataButtonInit()
            initRetryQueueDebug()
            initStorageBreakdown()
        }

        private fun blockGuestSwitches() {
            if (user?.id?.startsWith("guest") != true) return

            fun processPreference(pref: Preference) {
                when (pref) {
                    is SwitchPreference -> {
                        pref.onPreferenceChangeListener = OnPreferenceChangeListener { _, _ ->
                            DialogUtils.guestDialog(requireContext())
                            false
                        }
                    }
                    is androidx.preference.PreferenceGroup -> {
                        for (i in 0 until pref.preferenceCount) {
                            processPreference(pref.getPreference(i))
                        }
                    }
                }
            }

            for (i in 0 until preferenceScreen.preferenceCount) {
                processPreference(preferenceScreen.getPreference(i))
            }
        }

        private fun initStorageBreakdown() {
            refreshStorageBreakdownSummary()
            findPreference<Preference>("storage_breakdown")?.setOnPreferenceClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    val userModel = profileDbHandler.getUserModel()
                    if (userModel?.id?.startsWith("guest") == true) {
                        DialogUtils.guestDialog(requireActivity())
                    } else {
                        StorageBreakdownFragment().show(parentFragmentManager, "storage_breakdown")
                    }
                }
                true
            }
            parentFragmentManager.setFragmentResultListener(
                StorageBreakdownFragment.RESULT_KEY,
                this
            ) { _, _ -> refreshStorageBreakdownSummary() }
        }

        private fun refreshStorageBreakdownSummary() {
            findPreference<Preference>("storage_breakdown")?.summary = getString(R.string.storage_breakdown_summary) +
                " · ${getString(R.string.available_space_colon)} ${FileUtils.availableOverTotalMemoryFormattedString(requireContext())}"
        }

        private fun initRetryQueueDebug() {
            val retryQueuePref = findPreference<Preference>("debug_retry_queue")
            retryQueuePref?.onPreferenceClickListener = OnPreferenceClickListener {
                showRetryQueueDialog()
                true
            }
        }

        private fun showRetryQueueDialog() {
            viewModel.fetchRetryQueueDetails()
        }

        private fun clearDataButtonInit() {
            val preference = findPreference<Preference>("reset_app")
            if (preference != null) {
                preference.onPreferenceClickListener = OnPreferenceClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val userModel = profileDbHandler.getUserModel()
                        if (userModel?.id?.startsWith("guest") == true) {
                            DialogUtils.guestDialog(requireActivity())
                            return@launch
                        }
                        AlertDialog.Builder(requireActivity())
                            .setTitle(R.string.are_you_sure)
                            .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                                viewModel.clearAllData()
                            }
                            .setNegativeButton(R.string.no, null)
                            .show()
                    }
                    false
                }
            }
        }

        private fun setBetaToggleOn() {
            val beta = findPreference<SwitchPreference>("beta_function")

            beta?.onPreferenceChangeListener = OnPreferenceChangeListener { _: Preference?, _: Any? ->
                true
            }
        }

        private fun setAutoSyncToggleOn() {
            val autoSync = findPreference<SwitchPreference>("auto_sync_with_server") ?: return
            val autoForceWeeklySync = findPreference<SwitchPreference>("force_weekly_sync") ?: return
            val autoForceMonthlySync = findPreference<SwitchPreference>("force_monthly_sync") ?: return
            val lastSyncDate = findPreference<Preference>("lastSyncDate")
            autoSync.onPreferenceChangeListener = OnPreferenceChangeListener { _: Preference?, _: Any? ->
                if (autoSync.isChecked) {
                    if (autoForceWeeklySync.isChecked) {
                        autoForceMonthlySync.isChecked = false
                    } else autoForceWeeklySync.isChecked = !autoForceMonthlySync.isChecked
                }
                true
            }
            autoForceSync(autoSync, autoForceWeeklySync, autoForceMonthlySync)
            autoForceSync(autoSync, autoForceMonthlySync, autoForceWeeklySync)
            val lastSynced = sharedPrefManager.getLastSync()
            if (lastSynced == 0L) {
                lastSyncDate?.setTitle(R.string.last_synced_never)
            } else if (lastSyncDate != null) {
                lastSyncDate.title = getString(R.string.last_synced_colon) + TimeUtils.getRelativeTime(lastSynced, timeProvider)
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
                val currentLanguage = LocaleUtils.getLanguage(context)
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
                        LocaleUtils.setLocale(context, selectedLanguage)
                        (context as Activity).recreate()
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)

                val dialog = builder.create()
                dialog.show()

                if (context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    val maxHeight = (context.resources.displayMetrics.heightPixels * 0.35).toInt()
                    dialog.listView?.let { listView ->
                        val params = listView.layoutParams
                        params.height = maxHeight
                        listView.layoutParams = params
                    }
                }
            }

            fun textSizeChanger(context: Context) {
                val scales = floatArrayOf(0.85f, 1.0f, 1.15f)
                val options = arrayOf(
                    context.getString(R.string.text_size_small),
                    context.getString(R.string.text_size_medium),
                    context.getString(R.string.text_size_large)
                )
                val currentScale = LocaleUtils.getTextScale(context)
                var checkedItem = 1
                for (i in scales.indices) {
                    if (scales[i] == currentScale) {
                        checkedItem = i
                        break
                    }
                }

                val builder = AlertDialog.Builder(context, R.style.AlertDialogTheme)
                    .setTitle(context.getString(R.string.select_text_size))
                    .setSingleChoiceItems(ArrayAdapter(context, R.layout.checked_list_item, options), checkedItem) { dialog, which ->
                        LocaleUtils.setTextScale(context, scales[which])
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
