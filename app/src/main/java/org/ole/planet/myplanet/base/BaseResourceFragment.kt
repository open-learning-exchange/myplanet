package org.ole.planet.myplanet.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmResults
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.DownloadService
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.ui.components.CheckboxListView
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.submissions.SubmissionsAdapter
import org.ole.planet.myplanet.utils.DialogUtils
import org.ole.planet.myplanet.utils.DialogUtils.getProgressDialog
import org.ole.planet.myplanet.utils.DialogUtils.showError
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
abstract class BaseResourceFragment : Fragment() {
    var homeItemClickListener: OnHomeItemClickListener? = null
    var model: RealmUser? = null
    protected lateinit var mRealm: Realm
    var editor: SharedPreferences.Editor? = null
    internal lateinit var prgDialog: DialogUtils.CustomProgressDialog
    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var resourcesRepository: ResourcesRepository
    @Inject
    lateinit var coursesRepository: CoursesRepository
    @Inject
    lateinit var submissionsRepository: SubmissionsRepository
    @Inject
    lateinit var configurationsRepository: ConfigurationsRepository
    @Inject
    lateinit var databaseService: DatabaseService
    @Inject
    lateinit var profileDbHandler: UserSessionManager
    @Inject
    @AppPreferences
    lateinit var settings: SharedPreferences
    @Inject
    lateinit var broadcastService: org.ole.planet.myplanet.services.BroadcastService
    private var resourceNotFoundDialog: AlertDialog? = null
    private var pendingSurveyDialog: AlertDialog? = null
    private var stayOnlineDialog: AlertDialog? = null
    private var broadcastJob: Job? = null

    protected fun requireRealmInstance(): Realm {
        if (!isRealmInitialized()) {
            // mRealm initialized in onViewCreated
        }
        return mRealm
    }

    protected fun isRealmInitialized(): Boolean {
        return ::mRealm.isInitialized && !mRealm.isClosed
    }

    private fun isFragmentActive(): Boolean {
        return isAdded && activity != null &&
            !requireActivity().isFinishing && !requireActivity().isDestroyed
    }

    internal fun showProgressDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (isFragmentActive()) {
                prgDialog.setIndeterminateMode(true)
                prgDialog.show()
            }
        }
    }

    internal fun showNotConnectedToast() {
        if (isFragmentActive()) {
            Utilities.toast(requireActivity(),
                getString(R.string.device_not_connected_to_planet))
        }
    }

    private var stateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pendingResult = goAsync()
            stayOnlineDialog?.dismiss()
            stayOnlineDialog = AlertDialog.Builder(requireContext())
                .setMessage(R.string.do_you_want_to_stay_online)
                .setPositiveButton(R.string.yes) { _, _ ->
                    pendingResult.finish()
                }
                .setNegativeButton(R.string.no) { _, _ ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                        try {
                            startActivity(panelIntent)
                        } catch (e: Exception) {
                            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        }
                    } else {
                        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    }
                    pendingResult.finish()
                }
                .setCancelable(false)
                .create()
            stayOnlineDialog?.setOnDismissListener {
                stayOnlineDialog = null
            }
            stayOnlineDialog?.show()
        }
    }
    private val pendingDownloadUrls = mutableSetOf<String>()

    fun trackDownloadUrls(urls: Collection<String>) {
        pendingDownloadUrls.clear()
        pendingDownloadUrls.addAll(urls)
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == DashboardActivity.MESSAGE_PROGRESS) {
                val download: Download? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("download", Download::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("download")
                }
                if (download?.failed == false) {
                    if (pendingDownloadUrls.isNotEmpty()) {
                        val fileUrl = download.fileUrl
                        if (!fileUrl.isNullOrEmpty() && download.progress == 100) {
                            pendingDownloadUrls.remove(fileUrl)
                        }
                        setProgress(download.apply { completeAll = pendingDownloadUrls.isEmpty() })
                    } else {
                        setProgress(download)
                    }
                } else {
                    pendingDownloadUrls.clear()
                    prgDialog.dismiss()
                    download?.message?.let { showError(prgDialog, it) }
                }
            }
        }
    }

    fun showPendingSurveyDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val user = profileDbHandler.getUserModel()
            val list = submissionsRepository.getPendingSurveys(user?.id)
            if (list.isEmpty()) return@launch
            val exams = submissionsRepository.getExamMap(list)
            val arrayAdapter = createSurveyAdapter(list, exams)
            pendingSurveyDialog?.dismiss()
            pendingSurveyDialog = AlertDialog.Builder(requireActivity()).setTitle("Pending Surveys")
                .setAdapter(arrayAdapter) { _: DialogInterface?, i: Int ->
                    SubmissionsAdapter.openSurvey(homeItemClickListener, list[i].id, true, false, "")
                }.setPositiveButton(R.string.dismiss, null).create()
            pendingSurveyDialog?.setOnDismissListener {
                pendingSurveyDialog = null
            }
            pendingSurveyDialog?.show()
        }
    }

    private fun createSurveyAdapter(
        list: List<RealmSubmission>,
        exams: Map<String?, RealmStepExam>
    ): ArrayAdapter<RealmSubmission> {
        return object : ArrayAdapter<RealmSubmission>(requireActivity(), android.R.layout.simple_list_item_1, list) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(activity)
                    .inflate(android.R.layout.simple_list_item_1, parent, false)
                val text = exams[list[position].parentId]?.name ?: getString(R.string.n_a)
                (view as TextView).text = text
                return view
            }
        }
    }

    private val resourceNotFoundReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            showResourceNotFoundDialog()
        }
    }

    private fun showResourceNotFoundDialog() {
        if (isAdded) {
            if (prgDialog.isShowing()) {
                prgDialog.dismiss()
            }

            if (resourceNotFoundDialog?.isShowing == true) {
                return
            }

            resourceNotFoundDialog = AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                .setTitle(R.string.resource_not_found)
                .setMessage(R.string.resource_not_found_message)
                .setNegativeButton(R.string.close) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
            resourceNotFoundDialog?.setOnDismissListener {
                resourceNotFoundDialog = null
            }

            resourceNotFoundDialog?.show()
        }
    }

    fun setProgress(download: Download) {
        prgDialog.setProgress(download.progress)
        if (!TextUtils.isEmpty(download.fileName)) {
            prgDialog.setTitle(download.fileName)
        }
        if (download.completeAll) {
            onDownloadComplete()
        }
    }

    open fun onDownloadComplete() {
        prgDialog.dismiss()

        if (settings.getBoolean("isAlternativeUrl", false)) {
            editor?.putString("alternativeUrl", "")
            editor?.putString("processedAlternativeUrl", "")
            editor?.putBoolean("isAlternativeUrl", false)
            editor?.apply()
        }
    }

    private fun registerReceiver() {
        broadcastJob?.cancel()
        broadcastJob = lifecycleScope.launch {
            broadcastService.events.collect { intent ->
                if (isActive) {
                    when (intent.action) {
                        DashboardActivity.MESSAGE_PROGRESS -> broadcastReceiver.onReceive(requireContext(), intent)
                        "SHOW_WIFI_ALERT" -> stateReceiver.onReceive(requireContext(), intent)
                        DownloadService.RESOURCE_NOT_FOUND_ACTION -> resourceNotFoundReceiver.onReceive(requireContext(), intent)
                    }
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // mRealm initialized in onViewCreated
        prgDialog = getProgressDialog(requireActivity())
        editor = settings.edit()
    }

    override fun onPause() {
        super.onPause()
        broadcastJob?.cancel()
    }

    override fun onDetach() {
        super.onDetach()
        homeItemClickListener = null
    }

    override fun onResume() {
        super.onResume()
        registerReceiver()
    }

    fun showTagText(list: List<RealmTag>, tvSelected: TextView?) {
        val selected = list.joinToString(separator = ",", prefix = getString(R.string.selected)) { it.name.orEmpty() }
        tvSelected?.text = selected
    }

    override fun onDestroyView() {
        pendingSurveyDialog?.dismiss()
        pendingSurveyDialog = null
        stayOnlineDialog?.dismiss()
        stayOnlineDialog = null
        resourceNotFoundDialog?.dismiss()
        resourceNotFoundDialog = null
        broadcastJob?.cancel()
        super.onDestroyView()
    }

    override fun onDestroy() {
        cleanupRealm()
        super.onDestroy()
    }

    private fun cleanupRealm() {
        if (isRealmInitialized()) {
            try {
                mRealm.removeAllChangeListeners()
                if (mRealm.isInTransaction) {
                    try {
                        mRealm.commitTransaction()
                    } catch (e: Exception) {
                        mRealm.cancelTransaction()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (!mRealm.isClosed) {
                    mRealm.close()
                }
            }
        }
    }

    companion object {
        var auth = ""
    }
}
