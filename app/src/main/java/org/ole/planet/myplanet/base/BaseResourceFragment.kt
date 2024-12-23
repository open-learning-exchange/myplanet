package org.ole.planet.myplanet.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.realm.kotlin.Realm
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.datamanager.Service.PlanetAvailableListener
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onRemove
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.getExamMap
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.utilities.CheckboxListView
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.DialogUtils.getProgressDialog
import org.ole.planet.myplanet.utilities.DialogUtils.showError
import org.ole.planet.myplanet.utilities.DownloadUtils.downloadAllFiles
import org.ole.planet.myplanet.utilities.DownloadUtils.downloadFiles
import org.ole.planet.myplanet.utilities.Utilities

abstract class BaseResourceFragment : Fragment() {
    var homeItemClickListener: OnHomeItemClickListener? = null
    var model: RealmUserModel? = null
    lateinit var mRealm: Realm
    lateinit var profileDbHandler: UserProfileDbHandler
    var editor: SharedPreferences.Editor? = null
    var lv: CheckboxListView? = null
    var convertView: View? = null
    private lateinit var prgDialog: DialogUtils.CustomProgressDialog

    private var receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            showDownloadDialog(getLibraryList(DatabaseService().realmInstance))
        }
    }

    private var stateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AlertDialog.Builder(requireContext()).setMessage(R.string.do_you_want_to_stay_online)
                .setPositiveButton(R.string.yes, null)
                .setNegativeButton(R.string.no) { _: DialogInterface?, _: Int ->
                    val wifi = MainApplication.context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    wifi.setWifiEnabled(false)
                }.show()
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
                if (!download?.failed!!) {
                    setProgress(download)
                } else {
                    download.message?.let { showError(prgDialog, it) }
                }
            }
        }
    }

    protected fun showDownloadDialog(dbMyLibrary: List<RealmMyLibrary>) {
        if (isAdded) {
            Service(MainApplication.context).isPlanetAvailable(object : PlanetAvailableListener {
                override fun isAvailable() {
                    if (dbMyLibrary.isNotEmpty()) {
                        if (!isAdded) {
                            return
                        }
                        val inflater = activity?.layoutInflater
                        val rootView = requireActivity().findViewById<ViewGroup>(android.R.id.content)
                        convertView = inflater?.inflate(R.layout.my_library_alertdialog, rootView, false)
                        val alertDialogBuilder = AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                        alertDialogBuilder.setView(convertView).setTitle(R.string.download_suggestion)
                        alertDialogBuilder.setPositiveButton(R.string.download_selected) { _: DialogInterface?, _: Int ->
                            lv?.selectedItemsList?.let {
                                lifecycleScope.launch {
                                    addToLibrary(dbMyLibrary, it)
                                }
                                downloadFiles(dbMyLibrary, it)
                            }?.let { startDownload(it) }
                        }.setNeutralButton(R.string.download_all) { _: DialogInterface?, _: Int ->
                            lv?.selectedItemsList?.let {
                                lifecycleScope.launch {
                                    addAllToLibrary(dbMyLibrary)
                                }
                            }
                            startDownload(downloadAllFiles(dbMyLibrary))
                        }.setNegativeButton(R.string.txt_cancel, null)
                        val alertDialog = alertDialogBuilder.create()
                        createListView(dbMyLibrary, alertDialog)
                        alertDialog.show()
                        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = (lv?.selectedItemsList?.size ?: 0) > 0
                    } else {
                        Utilities.toast(requireContext(), getString(R.string.no_resources_to_download))
                    }
                }

                override fun notAvailable() {
                    if (!isAdded) {
                        return
                    }
                    Utilities.toast(requireContext(), getString(R.string.planet_not_available))
                }
            })
        }
    }

    fun showPendingSurveyDialog() {
        model = UserProfileDbHandler(requireContext()).userModel
        val list = mRealm.query<RealmSubmission>(RealmSubmission::class,
            "userId == $0 AND status == $1 AND type == $2",
            model?.id, "pending", "survey"
        ).find()

        if (list.isEmpty()) {
            return
        }

        val exams = getExamMap(mRealm, list)
        val arrayAdapter: ArrayAdapter<*> = object : ArrayAdapter<Any?>(requireActivity(), android.R.layout.simple_list_item_1, list) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var convertedView = convertView
                if (convertedView == null) {
                    convertedView = LayoutInflater.from(activity).inflate(android.R.layout.simple_list_item_1, parent, false)
                }
                if (exams.containsKey((getItem(position) as RealmSubmission?)?.parentId)) {
                    (convertedView as TextView?)?.text = exams[list[position].parentId]?.name
                } else {
                    (convertedView as TextView?)?.setText(R.string.n_a)
                }
                return convertedView!!
            }
        }

        AlertDialog.Builder(requireActivity()).setTitle("Pending Surveys")
            .setAdapter(arrayAdapter) { _: DialogInterface?, i: Int ->
                AdapterMySubmission.openSurvey(homeItemClickListener, list[i].id, true)
            }.setPositiveButton(R.string.dismiss, null).show()
    }

    fun startDownload(urls: ArrayList<String>) {
        if (isAdded) {
            Service(requireActivity()).isPlanetAvailable(object : PlanetAvailableListener {
                override fun isAvailable() {
                    if (urls.isNotEmpty()) {
                        prgDialog.show()
                        Utilities.openDownloadService(activity, urls, false)
                    }
                }

                override fun notAvailable() {
                    if (isAdded) {
                        Utilities.toast(requireActivity(), getString(R.string.device_not_connected_to_planet))
                    }
                }
            })
        }
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
    }

    fun createListView(dbMyLibrary: List<RealmMyLibrary?>, alertDialog: AlertDialog) {
        lv = convertView?.findViewById(R.id.alertDialog_listView)
        val names = ArrayList<String?>()
        for (i in dbMyLibrary.indices) {
            names.add(dbMyLibrary[i]?.title)
        }
        val adapter = ArrayAdapter(requireActivity().baseContext, R.layout.rowlayout, R.id.checkBoxRowLayout, names)
        lv?.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        lv?.setCheckChangeListener {
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = (lv?.selectedItemsList?.size ?: 0) > 0
        }
        lv?.adapter = adapter
    }

    private fun registerReceiver() {
        val bManager = LocalBroadcastManager.getInstance(requireActivity())
        val intentFilter = IntentFilter()
        intentFilter.addAction(DashboardActivity.MESSAGE_PROGRESS)
        bManager.registerReceiver(broadcastReceiver, intentFilter)

        val intentFilter2 = IntentFilter()
        intentFilter2.addAction("ACTION_NETWORK_CHANGED")
        LocalBroadcastManager.getInstance(MainApplication.context).registerReceiver(receiver, intentFilter2)

        val intentFilter3 = IntentFilter()
        intentFilter3.addAction("SHOW_WIFI_ALERT")
        LocalBroadcastManager.getInstance(MainApplication.context).registerReceiver(stateReceiver, intentFilter3)
    }

    fun getLibraryList(realm: Realm): List<RealmMyLibrary> {
        return getLibraryList(realm, settings?.getString("userId", "--"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mRealm = DatabaseService().realmInstance
        prgDialog = getProgressDialog(requireActivity())
        settings = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        editor = settings?.edit()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(receiver)
        LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(broadcastReceiver)
        LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(stateReceiver)
    }

    suspend fun removeFromShelf(`object`: RealmObject) {
        when (`object`) {
            is RealmMyLibrary -> {
                val myObject = mRealm.query<RealmMyLibrary>(
                    RealmMyLibrary::class,
                    "resourceId == $0", `object`.resourceId
                ).first().find()

                mRealm.write {
                    myObject?.removeUserId(model?.id)
                }

                model?.id?.let { userId ->
                    onRemove(mRealm, "resources", userId, `object`.resourceId)
                }
                withContext(Dispatchers.Main) {
                    Utilities.toast(activity, getString(R.string.removed_from_mylibrary))
                }
            }
            is RealmMyCourse -> {
                val myObject = getMyCourse(mRealm, `object`.courseId)
                mRealm.write {
                    myObject?.removeUserId(model?.id ?: "")
                }
                model?.id?.let { userId ->
                    onRemove(mRealm, "courses", userId, `object`.courseId)
                }
                Utilities.toast(activity, getString(R.string.removed_from_mycourse))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver()
    }

    fun showTagText(list: List<RealmTag>, tvSelected: TextView?) {
        val selected = StringBuilder(getString(R.string.selected))
        for (tags in list) {
            selected.append(tags.name).append(",")
        }
        tvSelected?.text = selected.subSequence(0, selected.length - 1)
    }

    suspend fun addToLibrary(libraryItems: List<RealmMyLibrary>, selectedItems: ArrayList<Int>) {
        mRealm.write {
            for (i in selectedItems.indices) {
                if (!libraryItems[selectedItems[i]].userId.contains(profileDbHandler.userModel?.id)) {
                    libraryItems[selectedItems[i]].setUserId(profileDbHandler.userModel?.id)
                    RealmRemovedLog.onAdd(mRealm, "resources", profileDbHandler.userModel?.id, libraryItems[selectedItems[i]].resourceId)
                }
            }
        }
        Utilities.toast(activity, getString(R.string.added_to_my_library))
    }

    suspend fun addAllToLibrary(libraryItems: List<RealmMyLibrary>) {
        mRealm.write {
            for (libraryItem in libraryItems) {
                if (!libraryItem.userId.contains(profileDbHandler.userModel?.id)) {
                    libraryItem.setUserId(profileDbHandler.userModel?.id)
                    RealmRemovedLog.onAdd(mRealm, "resources", profileDbHandler.userModel?.id, libraryItem.resourceId)
                }
            }
        }
        Utilities.toast(activity, getString(R.string.added_to_my_library))
    }

    companion object {
        var settings: SharedPreferences? = null
        var auth = ""

        fun getAllLibraryList(realm: Realm): List<RealmMyLibrary> {
            val l = realm.query<RealmMyLibrary>(RealmMyLibrary::class, "resourceOffline == $0", false).find()
            val libList = mutableListOf<RealmMyLibrary>()
            val libraries = getLibraries(l)
            libList.addAll(libraries)
            return libList
        }

        fun backgroundDownload(urls: ArrayList<String>) {
            Service(context).isPlanetAvailable(object : PlanetAvailableListener {
                override fun isAvailable() {
                    if (urls.isNotEmpty()) {
                        Utilities.openDownloadService(context, urls, false)
                    }
                }

                override fun notAvailable() {}
            })
        }

        fun getLibraryList(realm: Realm, userId: String?): List<RealmMyLibrary> {
            val l = realm.query<RealmMyLibrary>(RealmMyLibrary::class, "isPrivate == $0", false).find()
            val libList = mutableListOf<RealmMyLibrary>()
            val libraries = getLibraries(l)
            for (item in libraries) {
                if (item.userId.contains(userId)) {
                    libList.add(item)
                }
            }
            return libList
        }

        private fun getLibraries(l: List<RealmMyLibrary>): List<RealmMyLibrary> {
            return l.filter { it.needToUpdate() }
        }
    }
}
