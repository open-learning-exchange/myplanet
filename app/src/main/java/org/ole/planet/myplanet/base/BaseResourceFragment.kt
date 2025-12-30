package org.ole.planet.myplanet.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.wifi.WifiManager
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
import org.ole.planet.myplanet.data.Service
import org.ole.planet.myplanet.data.Service.PlanetAvailableListener
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onRemove
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.getExamMap
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.MyDownloadService
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.submission.SubmissionsAdapter
import org.ole.planet.myplanet.ui.components.CheckboxListView
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.DialogUtils.getProgressDialog
import org.ole.planet.myplanet.utilities.DialogUtils.showError
import org.ole.planet.myplanet.utilities.DownloadUtils
import org.ole.planet.myplanet.utilities.DownloadUtils.downloadAllFiles
import org.ole.planet.myplanet.utilities.DownloadUtils.downloadFiles
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
abstract class BaseResourceFragment : Fragment() {
    var homeItemClickListener: OnHomeItemClickListener? = null
    var model: RealmUserModel? = null
    protected lateinit var mRealm: Realm
    var editor: SharedPreferences.Editor? = null
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
    lateinit var submissionRepository: SubmissionsRepository
    @Inject
    lateinit var databaseService: DatabaseService
    @Inject
    lateinit var profileDbHandler: UserProfileDbHandler
    @Inject
    @AppPreferences
    lateinit var settings: SharedPreferences
    @Inject
    lateinit var broadcastService: org.ole.planet.myplanet.service.BroadcastService
    private var resourceNotFoundDialog: AlertDialog? = null
    private var downloadSuggestionDialog: AlertDialog? = null
    private var pendingSurveyDialog: AlertDialog? = null
    private var stayOnlineDialog: AlertDialog? = null
    private var broadcastJob: Job? = null

    protected fun requireRealmInstance(): Realm {
        if (!isRealmInitialized()) {
            mRealm = databaseService.realmInstance
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

    private fun showProgressDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (isFragmentActive()) {
                prgDialog.show()
            }
        }
    }

    private fun showNotConnectedToast() {
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
                    val list = resourcesRepository.getLibraryListForUser(
                        settings.getString("userId", "--")
                    )
                    showDownloadDialog(list)
                } finally {
                    pendingResult.finish()
                }
            }
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
                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val wifi = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                            wifi.setWifiEnabled(false)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
                .setCancelable(false)
                .create()
            stayOnlineDialog?.setOnDismissListener {
                stayOnlineDialog = null
            }
            stayOnlineDialog?.show()
        }
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
                    setProgress(download)
                } else {
                    prgDialog.dismiss()
                    download?.message?.let { showError(prgDialog, it) }
                }
            }
        }
    }

    protected fun showDownloadDialog(dbMyLibrary: List<RealmMyLibrary?>) {
        if (!isAdded) return
        Service(requireContext()).isPlanetAvailable(object : PlanetAvailableListener {
            override fun isAvailable() {
                if (!isAdded) return
                val userId = profileDbHandler.userModel?.id
                val librariesForDialog = if (userId.isNullOrBlank()) {
                    dbMyLibrary
                } else {
                    val userLibraries = dbMyLibrary.filter { it?.userId?.contains(userId) == true }
                    if (userLibraries.isEmpty()) dbMyLibrary else userLibraries
                }

                if (librariesForDialog.isEmpty()) {
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
                            lv?.selectedItemsList?.let {
                                addToLibrary(librariesForDialog, it)
                                downloadFiles(librariesForDialog, it)
                            }?.let { startDownload(it) }
                        }.setNeutralButton(R.string.download_all) { _: DialogInterface?, _: Int ->
                            lv?.selectedItemsList?.let {
                                addAllToLibrary(librariesForDialog)
                            }
                            startDownload(downloadAllFiles(librariesForDialog))
                        }.setNegativeButton(R.string.txt_cancel, null)
                    downloadSuggestionDialog?.dismiss()
                    downloadSuggestionDialog = alertDialogBuilder.create()
                    downloadSuggestionDialog?.let { dialog ->
                        createListView(librariesForDialog, dialog)
                        dialog.setOnDismissListener {
                            downloadSuggestionDialog = null
                        }
                        dialog.show()
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = (lv?.selectedItemsList?.size
                            ?: 0) > 0
                    }
                }
            }

            override fun notAvailable() {
                if (!isAdded) return
                activity?.let {
                    Utilities.toast(it, getString(R.string.planet_not_available))
                }
            }
        })
    }

    fun showPendingSurveyDialog() {
        model = profileDbHandler.userModel
        viewLifecycleOwner.lifecycleScope.launch {
            val list = submissionRepository.getPendingSurveys(model?.id)
            if (list.isEmpty()) return@launch
            val exams = getExamMap(mRealm, list)
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
        exams: HashMap<String?, RealmStepExam>
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

    fun startDownload(urls: ArrayList<String>) {
        if (!isFragmentActive()) return
        Service(requireActivity()).isPlanetAvailable(object : PlanetAvailableListener {
            override fun isAvailable() {
                if (!isFragmentActive()) return
                if (urls.isNotEmpty()) {
                    try {
                        showProgressDialog()
                        DownloadUtils.openDownloadService(activity, urls, false)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            override fun notAvailable() {
                showNotConnectedToast()
            }
        })
    }

    fun setProgress(download: Download) {
        prgDialog.setProgress(download.progress)
        if (!TextUtils.isEmpty(download.fileName)) {
            prgDialog.setTitle(download.fileName)
        }
        if (download.completeAll) {
            showError(prgDialog, getString(R.string.all_files_downloaded_successfully))
            onDownloadComplete()
        }
    }

    open fun onDownloadComplete() {
        prgDialog.setPositiveButton("Finish", isVisible = true){
            prgDialog.dismiss()
        }
        prgDialog.setNegativeButton("disabling", isVisible = false){ prgDialog.dismiss() }

        if (settings.getBoolean("isAlternativeUrl", false)) {
            editor?.putString("alternativeUrl", "")
            editor?.putString("processedAlternativeUrl", "")
            editor?.putBoolean("isAlternativeUrl", false)
            editor?.apply()
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
        broadcastJob?.cancel()
        broadcastJob = lifecycleScope.launch {
            broadcastService.events.collect { intent ->
                if (isActive) {
                    when (intent.action) {
                        DashboardActivity.MESSAGE_PROGRESS -> broadcastReceiver.onReceive(requireContext(), intent)
                        "ACTION_NETWORK_CHANGED" -> receiver.onReceive(requireContext(), intent)
                        "SHOW_WIFI_ALERT" -> stateReceiver.onReceive(requireContext(), intent)
                        MyDownloadService.RESOURCE_NOT_FOUND_ACTION -> resourceNotFoundReceiver.onReceive(requireContext(), intent)
                    }
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mRealm = databaseService.realmInstance
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

    fun removeFromShelf(`object`: RealmObject) {
        if (`object` is RealmMyLibrary) {
            val myObject = mRealm.where(RealmMyLibrary::class.java).equalTo("resourceId", `object`.resourceId).findFirst()
            myObject?.removeUserId(model?.id)
            model?.id?.let { `object`.resourceId?.let { it1 ->
                onRemove(mRealm, "resources", it, it1)
            } }
            Utilities.toast(activity, getString(R.string.removed_from_mylibrary))
        } else {
            val myObject = getMyCourse(mRealm, (`object` as RealmMyCourse).courseId)
            myObject?.removeUserId(model?.id)
            model?.id?.let { `object`.courseId?.let { it1 -> onRemove(mRealm, "courses", it, it1) } }
            Utilities.toast(activity, getString(R.string.removed_from_mycourse))
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver()
    }

    fun showTagText(list: List<RealmTag>, tvSelected: TextView?) {
        val selected = list.joinToString(separator = ",", prefix = getString(R.string.selected)) { it.name.orEmpty() }
        tvSelected?.text = selected
    }

    fun addToLibrary(libraryItems: List<RealmMyLibrary?>, selectedItems: ArrayList<Int>) {
        if (!isRealmInitialized()) return
        
        val userId = profileDbHandler.userModel?.id ?: return

        try {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }

            selectedItems.forEach { index ->
                val item = libraryItems[index]
                if (item?.userId?.contains(userId) == false) {
                    item.setUserId(userId)
                    RealmRemovedLog.onAdd(mRealm, "resources", userId, item.resourceId)
                }
            }
            
            if (mRealm.isInTransaction) {
                mRealm.commitTransaction()
            }
        } catch (e: Exception) {
            if (mRealm.isInTransaction) {
                mRealm.cancelTransaction()
            }
            throw e
        }
        Utilities.toast(activity, getString(R.string.added_to_my_library))
    }

    fun addAllToLibrary(libraryItems: List<RealmMyLibrary?>) {
        if (!isRealmInitialized()) return

        val userId = profileDbHandler.userModel?.id ?: return

        try {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }

            libraryItems.forEach { libraryItem ->
                if (libraryItem?.userId?.contains(userId) == false) {
                    libraryItem.setUserId(userId, mRealm)
                    RealmRemovedLog.onAdd(mRealm, "resources", userId, libraryItem.resourceId)
                }
            }
            
            if (mRealm.isInTransaction) {
                mRealm.commitTransaction()
            }
        } catch (e: Exception) {
            if (mRealm.isInTransaction) {
                mRealm.cancelTransaction()
            }
            throw e
        }
        Utilities.toast(activity, getString(R.string.added_to_my_library))
    }

    override fun onDestroyView() {
        downloadSuggestionDialog?.dismiss()
        downloadSuggestionDialog = null
        pendingSurveyDialog?.dismiss()
        pendingSurveyDialog = null
        stayOnlineDialog?.dismiss()
        stayOnlineDialog = null
        resourceNotFoundDialog?.dismiss()
        resourceNotFoundDialog = null
        convertView = null
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

        fun getAllLibraryList(mRealm: Realm): List<RealmMyLibrary> {
            val l = mRealm.where(RealmMyLibrary::class.java).equalTo("resourceOffline", false).findAll()
            val libList: MutableList<RealmMyLibrary> = ArrayList()
            val libraries = getLibraries(l)
            libList.addAll(libraries)
            return libList
        }

        fun backgroundDownload(urls: ArrayList<String>, context: Context) {
            Service(context).isPlanetAvailable(object : PlanetAvailableListener {
                override fun isAvailable() {
                    if (urls.isNotEmpty()) {
                        DownloadUtils.openDownloadService(context, urls, false)
                    }
                }

                override fun notAvailable() {}
            })
        }

        private fun getLibraries(l: RealmResults<RealmMyLibrary>): List<RealmMyLibrary> {
            val libraries: MutableList<RealmMyLibrary> = ArrayList()
            for (lib in l) {
                if (lib.needToUpdate()) {
                    libraries.add(lib)
                }
            }
            return libraries
        }
    }
}
