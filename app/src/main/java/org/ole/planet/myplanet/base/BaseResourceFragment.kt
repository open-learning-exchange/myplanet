package org.ole.planet.myplanet.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.realm.RealmObject
import javax.inject.Inject
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
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
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.DownloadService
import org.ole.planet.myplanet.services.SharedPrefManager
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
    var lv: CheckboxListView? = null
    var convertView: View? = null
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
    lateinit var surveysRepository: SurveysRepository
    @Inject
    lateinit var configurationsRepository: ConfigurationsRepository
    @Inject
    lateinit var profileDbHandler: UserSessionManager
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    @Inject
    lateinit var broadcastService: org.ole.planet.myplanet.services.BroadcastService
    private var resourceNotFoundDialog: AlertDialog? = null
    private var downloadSuggestionDialog: AlertDialog? = null
    private var pendingSurveyDialog: AlertDialog? = null

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

    private var receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pendingResult = goAsync()
            this@BaseResourceFragment.lifecycleScope.launch {
                try {
                    val list = resourcesRepository.getDownloadSuggestionList()
                    showDownloadDialog(list)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
    private val pendingDownloadUrls = mutableSetOf<String>()

    protected fun trackDownloadUrls(urls: Collection<String>) {
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
                        if (!fileUrl.isNullOrEmpty() && fileUrl in pendingDownloadUrls) {
                            if (download.progress == 100) {
                                pendingDownloadUrls.remove(fileUrl)
                            }
                            setProgress(download.apply { completeAll = pendingDownloadUrls.isEmpty() })
                        }
                    }
                } else {
                    pendingDownloadUrls.clear()
                    prgDialog.dismiss()
                    download?.message?.let { showError(prgDialog, it) }
                }
            }
        }
    }

    protected fun showDownloadDialog(dbMyLibrary: List<RealmMyLibrary?>) {
        if (!isAdded) return
        if (dbMyLibrary.isEmpty()) {
            return
        }

        activity?.let { fragmentActivity ->
            val inflater = fragmentActivity.layoutInflater
            val rootView = fragmentActivity.findViewById<ViewGroup>(android.R.id.content)
            convertView = inflater.inflate(R.layout.my_library_alertdialog, rootView, false)

            val alertDialogBuilder = AlertDialog.Builder(fragmentActivity, R.style.AlertDialogTheme)
            alertDialogBuilder.setView(convertView)
                .setTitle(R.string.download_suggestion)
                .setPositiveButton(R.string.download_selected) { _: DialogInterface?, _: Int ->
                    lifecycleScope.launch {
                        if (configurationsRepository.checkServerAvailability()) {
                            lv?.selectedItemsList?.let {
                                addToLibrary(dbMyLibrary, it)
                                val selectedLibraries = it.mapNotNull { index ->
                                    dbMyLibrary.getOrNull(
                                        index
                                    ) }
                                if (resourcesRepository.downloadResources(selectedLibraries)) {
                                    trackDownloadUrls(selectedLibraries.mapNotNull { lib -> lib.resourceRemoteAddress })
                                    showProgressDialog()
                                }
                            }
                        } else {
                            showNotConnectedToast()
                        }
                    }
                }.setNeutralButton(R.string.download_all) { _: DialogInterface?, _: Int ->
                    lifecycleScope.launch {
                        if (configurationsRepository.checkServerAvailability()) {
                            addAllToLibrary(dbMyLibrary)
                            val filtered = dbMyLibrary.filterNotNull()
                            if (resourcesRepository.downloadResources(filtered)) {
                                trackDownloadUrls(filtered.mapNotNull { lib -> lib.resourceRemoteAddress })
                                showProgressDialog()
                            }
                        } else {
                            showNotConnectedToast()
                        }
                    }
                }.setNegativeButton(R.string.txt_cancel, null)
            downloadSuggestionDialog?.dismiss()
            downloadSuggestionDialog = alertDialogBuilder.create()
            downloadSuggestionDialog?.let { dialog ->
                createListView(dbMyLibrary, dialog)
                dialog.setOnDismissListener {
                    downloadSuggestionDialog = null
                }
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = (lv?.selectedItemsList?.size
                    ?: 0) > 0
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

        if (sharedPrefManager.isAlternativeUrl()) {
            sharedPrefManager.setAlternativeUrl("")
            sharedPrefManager.setProcessedAlternativeUrl("")
            sharedPrefManager.setIsAlternativeUrl(false)
        }
    }

    fun createListView(dbMyLibrary: List<RealmMyLibrary?>, alertDialog: AlertDialog) {
        lv = convertView?.findViewById(R.id.alertDialog_listView)
        val names = dbMyLibrary.map { it?.title }
        val adapter = ArrayAdapter(requireActivity().baseContext, R.layout.rowlayout, R.id.checkBoxRowLayout, names)
        lv?.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        lv?.setCheckChangeListener {
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = (lv?.selectedItemsList?.size ?: 0) > 0
        }
        lv?.adapter = adapter
    }

    private fun registerReceiver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                broadcastService.events.collect { intent ->
                    if (isActive) {
                        when (intent.action) {
                            DashboardActivity.MESSAGE_PROGRESS -> broadcastReceiver.onReceive(requireContext(), intent)
                            "ACTION_NETWORK_CHANGED" -> receiver.onReceive(requireContext(), intent)
                            DownloadService.RESOURCE_NOT_FOUND_ACTION -> resourceNotFoundReceiver.onReceive(requireContext(), intent)
                        }
                    }
                }
            }
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        registerReceiver()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prgDialog = getProgressDialog(requireActivity())
    }

    override fun onDetach() {
        super.onDetach()
        homeItemClickListener = null
    }

    fun removeFromShelf(`object`: RealmObject) {
        lifecycleScope.launch {
            val userId = profileDbHandler.getUserModel()?.id
            if (userId.isNullOrEmpty()) {
                return@launch
            }

            if (`object` is RealmMyLibrary) {
                val resourceId = `object`.resourceId
                if (resourceId != null) {
                    resourcesRepository.removeResourceFromShelf(resourceId, userId)
                    Utilities.toast(activity, getString(R.string.removed_from_mylibrary))
                }
            } else {
                val courseId = (`object` as RealmMyCourse).courseId
                if (courseId != null) {
                    coursesRepository.removeCourseFromShelf(courseId, userId)
                    Utilities.toast(activity, getString(R.string.removed_from_mycourse))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    fun showTagText(list: List<RealmTag>, tvSelected: TextView?) {
        val selected = list.joinToString(separator = ",", prefix = getString(R.string.selected)) { it.name.orEmpty() }
        tvSelected?.text = selected
    }

    fun addToLibrary(libraryItems: List<RealmMyLibrary?>, selectedItems: ArrayList<Int>) {
        lifecycleScope.launch {
            val userId = profileDbHandler.getUserModel()?.id ?: return@launch
            val resourceIds = selectedItems.mapNotNull { index ->
                libraryItems.getOrNull(index)?.resourceId
            }
            resourcesRepository.addResourcesToUserLibrary(resourceIds, userId)
                .onSuccess {
                    Utilities.toast(activity, getString(R.string.added_to_my_library))
                }
                .onFailure {
                    Utilities.toast(activity, getString(R.string.error, it.message))
                }
        }
    }

    fun addAllToLibrary(libraryItems: List<RealmMyLibrary?>) {
        lifecycleScope.launch {
            val user = profileDbHandler.getUserModel()
            val userId = user?.id ?: return@launch
            val validLibraryItems = libraryItems.filterNotNull()
            resourcesRepository.addAllResourcesToUserLibrary(validLibraryItems, userId)
                .onSuccess {
                    Utilities.toast(activity, getString(R.string.added_to_my_library))
                }
                .onFailure {
                    Utilities.toast(activity, getString(R.string.error, it.message))
                }
        }
    }

    override fun onDestroyView() {
        downloadSuggestionDialog?.dismiss()
        downloadSuggestionDialog = null
        pendingSurveyDialog?.dismiss()
        pendingSurveyDialog = null
        resourceNotFoundDialog?.dismiss()
        resourceNotFoundDialog = null
        convertView = null
        super.onDestroyView()
    }
}
