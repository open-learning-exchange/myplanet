package org.ole.planet.myplanet.ui.dashboard

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.DatePicker
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayout
import dagger.hilt.android.AndroidEntryPoint
import io.realm.RealmObject
import io.realm.Sort
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.NotificationCallback
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.databinding.AlertHealthListBinding
import org.ole.planet.myplanet.databinding.ItemLibraryHomeBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTeamNotification
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.TransactionSyncManager
import org.ole.planet.myplanet.service.UserProfileDbHandler.Companion.KEY_LOGIN
import org.ole.planet.myplanet.ui.exam.UserInformationFragment
import org.ole.planet.myplanet.ui.myhealth.UserListArrayAdapter
import org.ole.planet.myplanet.ui.team.TeamDetailFragment
import org.ole.planet.myplanet.ui.userprofile.BecomeMemberActivity
import org.ole.planet.myplanet.ui.userprofile.UserProfileFragment
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.DownloadUtils
import org.ole.planet.myplanet.utilities.DownloadUtils.downloadAllFiles
import org.ole.planet.myplanet.utilities.DownloadUtils.downloadFiles
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
open class BaseDashboardFragment : BaseDashboardFragmentPlugin(), NotificationCallback,
    SyncListener {
    private val viewModel: DashboardViewModel by viewModels()
    private var fullName: String? = null
    private var params = LinearLayout.LayoutParams(250, 100)
    private var di: DialogUtils.CustomProgressDialog? = null

    @Inject
    lateinit var transactionSyncManager: TransactionSyncManager

    fun onLoaded(v: View) {
        model = profileDbHandler.userModel
        fullName = profileDbHandler.userModel?.getFullName()
        if (fullName?.trim().isNullOrBlank()) {
            fullName = profileDbHandler.userModel?.name
            v.findViewById<LinearLayout>(R.id.ll_prompt).visibility = View.VISIBLE
            v.findViewById<LinearLayout>(R.id.ll_prompt).setOnClickListener {
                if (!childFragmentManager.isStateSaved) {
                    UserInformationFragment.getInstance("", "", false).show(childFragmentManager, "")
                }
            }
        } else {
            v.findViewById<LinearLayout>(R.id.ll_prompt).visibility = View.GONE
        }
        v.findViewById<ImageView>(R.id.ic_close).setOnClickListener {
            v.findViewById<LinearLayout>(R.id.ll_prompt).visibility = View.GONE
        }
        val imageView = v.findViewById<ImageView>(R.id.imageView)
        if (!TextUtils.isEmpty(model?.userImage)) {
            Glide.with(requireActivity())
                .load(model?.userImage)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(200, 200)
                .circleCrop()
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.profile)
        }

        v.findViewById<TextView>(R.id.txtRole).text = getString(R.string.user_role, model?.getRoleAsString())
        val offlineVisits = profileDbHandler.offlineVisits
        v.findViewById<TextView>(R.id.txtFullName).text = getString(R.string.user_name, fullName, offlineVisits)
    }

    override fun forceDownloadNewsImages() {
        Utilities.toast(activity, getString(R.string.please_select_starting_date))
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog(requireActivity(), { _: DatePicker?, i: Int, i1: Int, i2: Int ->
            now[Calendar.YEAR] = i
            now[Calendar.MONTH] = i1
            now[Calendar.DAY_OF_MONTH] = i2

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                var imageList: List<RealmMyLibrary> = emptyList()
                databaseService.withRealm { realm ->
                    imageList = realm.where(RealmMyLibrary::class.java).equalTo("isPrivate", true)
                        .greaterThan("createdDate", now.timeInMillis).equalTo("mediaType", "image")
                        .findAll().let { realm.copyFromRealm(it) }
                }
                withContext(Dispatchers.Main) {
                    val urls = ArrayList<String>()
                    getUrlsAndStartDownload(imageList, urls)
                }
            }
        }, now[Calendar.YEAR], now[Calendar.MONTH], now[Calendar.DAY_OF_MONTH])
        dpd.setTitle(getString(R.string.read_offline_news_from))
        dpd.show()
    }

    override fun downloadDictionary() {
        val list = ArrayList<String>()
        list.add(Constants.DICTIONARY_URL)
        if (!FileUtils.checkFileExist(requireContext(), Constants.DICTIONARY_URL)) {
            Utilities.toast(activity, getString(R.string.downloading_started_please_check_notification))
            DownloadUtils.openDownloadService(activity, list, false)
        } else {
            Utilities.toast(activity, getString(R.string.file_already_exists))
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect {
                renderMyLibrary(it.library)
                renderMyCourses(it.courses)
                renderMyTeams(it.teams)
            }
        }
    }

    private fun renderMyLibrary(dbMylibrary: List<RealmMyLibrary>) {
        val flexboxLayout = view?.findViewById<FlexboxLayout>(R.id.flexboxLayout)
        flexboxLayout?.removeAllViews()
        flexboxLayout?.flexDirection = FlexDirection.ROW
        if (dbMylibrary.isEmpty()) {
            view?.findViewById<TextView>(R.id.count_library)?.visibility = View.GONE
        } else {
            view?.findViewById<TextView>(R.id.count_library)?.text =
                getString(R.string.number_placeholder, dbMylibrary.size)
        }
        for ((itemCnt, items) in dbMylibrary.withIndex()) {
            val itemLibraryHomeBinding =
                ItemLibraryHomeBinding.inflate(LayoutInflater.from(activity))
            val v = itemLibraryHomeBinding.root
            setTextColor(itemLibraryHomeBinding.title, itemCnt)
            val colorResId =
                if (itemCnt % 2 == 0) R.color.card_bg else R.color.dashboard_item_alternative
            val color = context?.let { ContextCompat.getColor(it, colorResId) }
            if (color != null) {
                v.setBackgroundColor(color)
            }

            itemLibraryHomeBinding.title.text = items.title
            itemLibraryHomeBinding.detail.setOnClickListener {
                if (homeItemClickListener != null) {
                    homeItemClickListener?.openLibraryDetailFragment(items)
                }
            }

            myLibraryItemClickAction(itemLibraryHomeBinding.title, items)
            flexboxLayout?.addView(v, params)
        }
    }

    private fun renderMyCourses(courses: List<RealmMyCourse>) {
        val flexboxLayout: FlexboxLayout = view?.findViewById(R.id.flexboxLayoutCourse) ?: return
        flexboxLayout.removeAllViews()
        val filteredCourses = courses.filter { !it.courseTitle.isNullOrBlank() }
        setCountText(filteredCourses.size, RealmMyCourse::class.java, requireView())
        val myCoursesTextViewArray = arrayOfNulls<TextView>(filteredCourses.size)
        for ((itemCnt, items) in filteredCourses.withIndex()) {
            setTextViewProperties(myCoursesTextViewArray, itemCnt, items)
            myCoursesTextViewArray[itemCnt]?.let { setTextColor(it, itemCnt) }
            flexboxLayout.addView(myCoursesTextViewArray[itemCnt], params)
        }
    }

    private fun renderMyTeams(teams: List<RealmMyTeam>) {
        val flexboxLayout: FlexboxLayout = view?.findViewById(R.id.flexboxLayoutTeams) ?: return
        flexboxLayout.removeAllViews()
        val userId = profileDbHandler.userModel?.id
        for ((count, ob) in teams.withIndex()) {
            val v = LayoutInflater.from(activity).inflate(R.layout.item_home_my_team, flexboxLayout, false)
            val name = v.findViewById<TextView>(R.id.tv_name)
            setBackgroundColor(v, count)
            if (ob.teamType == "sync") {
                name.setTypeface(null, Typeface.BOLD)
            }
            handleClick(ob._id, ob.name, TeamDetailFragment(), name)
            showNotificationIcons(ob, v, userId)
            name.text = ob.name
            flexboxLayout.addView(v, params)
        }
        setCountText(teams.size, RealmMyTeam::class.java, requireView())
    }

    private fun showNotificationIcons(ob: RealmObject, v: View, userId: String?) {
        val current = Calendar.getInstance().timeInMillis
        val tomorrow = Calendar.getInstance()
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)
        val imgTask = v.findViewById<ImageView>(R.id.img_task)
        val imgChat = v.findViewById<ImageView>(R.id.img_chat)
        val teamId = (ob as RealmMyTeam)._id

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var showChat = false
            var showTask = false
            databaseService.withRealm { realm ->
                val notification = realm.where(RealmTeamNotification::class.java)
                    .equalTo("parentId", teamId).equalTo("type", "chat").findFirst()
                val chatCount = realm.where(RealmNews::class.java).equalTo("viewableBy", "teams")
                    .equalTo("viewableId", teamId).count()
                if (notification != null) {
                    showChat = notification.lastCount < chatCount
                }
                val tasks = realm.where(RealmTeamTask::class.java).equalTo("assignee", userId)
                    .between("deadline", current, tomorrow.timeInMillis).findAll()
                showTask = tasks.isNotEmpty()
            }
            withContext(Dispatchers.Main) {
                imgChat.visibility = if (showChat) View.VISIBLE else View.GONE
                imgTask.visibility = if (showTask) View.VISIBLE else View.GONE
            }
        }
    }

    private fun myLifeListInit(flexboxLayout: FlexboxLayout) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var dbMylife: List<RealmMyLife> = emptyList()
            var surveyCount = 0
            databaseService.withRealm { realm ->
                val rawMylife = RealmMyLife.getMyLifeByUserId(realm, settings)
                dbMylife = rawMylife.filter { it.isVisible }.map { realm.copyFromRealm(it) }

                if (dbMylife.any { it.title == getString(R.string.my_survey) }) {
                    val userId = settings?.getString("userId", "--")
                    surveyCount = RealmSubmission.getNoOfSurveySubmissionByUser(userId, realm)
                }
            }
            withContext(Dispatchers.Main) {
                for ((itemCnt, items) in dbMylife.withIndex()) {
                    flexboxLayout.addView(getLayout(itemCnt, items, surveyCount), params)
                }
            }
        }
    }

    private fun setUpMyLife(userId: String?) {
        databaseService.withRealm { realm ->
            val realmObjects = RealmMyLife.getMyLifeByUserId(realm, settings)
            if (realmObjects.isEmpty()) {
                if (!realm.isInTransaction) {
                    realm.beginTransaction()
                }
                val myLifeListBase = getMyLifeListBase(userId)
                var ml: RealmMyLife
                var weight = 1
                for (item in myLifeListBase) {
                    ml = realm.createObject(RealmMyLife::class.java, UUID.randomUUID().toString())
                    ml.title = item.title
                    ml.imageId = item.imageId
                    ml.weight = weight
                    ml.userId = item.userId
                    ml.isVisible = true
                    weight++
                }
                realm.commitTransaction()
            }
        }
    }

    private fun myLibraryItemClickAction(textView: TextView, items: RealmMyLibrary?) {
        textView.setOnClickListener {
            items?.let {
                openResource(it)
            }
        }
    }

    private fun setCountText(countText: Int, c: Class<*>, v: View) {
        when (c) {
            RealmMyCourse::class.java -> {
                updateCountText(countText, v.findViewById(R.id.count_course))
            }
            RealmMyTeam::class.java -> {
                updateCountText(countText, v.findViewById(R.id.count_team))
            }
        }
    }

    private fun updateCountText(countText: Int, tv: TextView) {
        tv.text = getString(R.string.number_placeholder, countText)
        hideCountIfZero(tv, countText)
    }

    private fun hideCountIfZero(v: View, count: Int) {
        v.visibility = if (count == 0) View.GONE else View.VISIBLE
    }

    fun initView(view: View) {
        view.findViewById<View>(R.id.imageView).setOnClickListener {
            homeItemClickListener?.openCallFragment(UserProfileFragment())
        }
        view.findViewById<View>(R.id.txtFullName).setOnClickListener {
            homeItemClickListener?.openCallFragment(UserProfileFragment())
        }
        viewModel.loadUserContent(settings?.getString("userId", "--"))
        observeUiState()

        view.findViewById<FlexboxLayout>(R.id.flexboxLayoutCourse).flexDirection = FlexDirection.ROW
        view.findViewById<FlexboxLayout>(R.id.flexboxLayoutTeams).flexDirection = FlexDirection.ROW
        val myLifeFlex = view.findViewById<FlexboxLayout>(R.id.flexboxLayoutMyLife)
        myLifeFlex.flexDirection = FlexDirection.ROW

        val userId = settings?.getString("userId", "--")
        setUpMyLife(userId)
        myLifeListInit(myLifeFlex)
    }

    override fun showResourceDownloadDialog() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val userId = settings?.getString("userId", "--")
            val libList = libraryRepository.getLibraryListForUser(userId)
            withContext(Dispatchers.Main) {
                showLibraryDownloadDialog(libList)
            }
        }
    }

    override fun showUserResourceDialog() {
        var dialog: AlertDialog? = null
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var userModelList: List<RealmUserModel> = emptyList()
            databaseService.withRealm { realm ->
                userModelList = realm.where(RealmUserModel::class.java).sort("joinDate", Sort.DESCENDING).findAll()
                    .let { realm.copyFromRealm(it) }
            }
            withContext(Dispatchers.Main) {
                val adapter = UserListArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, userModelList)
                val alertHealthListBinding = AlertHealthListBinding.inflate(LayoutInflater.from(activity))
                alertHealthListBinding.etSearch.visibility = View.GONE
                alertHealthListBinding.spnSort.visibility = View.GONE

                alertHealthListBinding.btnAddMember.setOnClickListener {
                    startActivity(Intent(requireContext(), BecomeMemberActivity::class.java))
                }

                alertHealthListBinding.list.adapter = adapter
                alertHealthListBinding.list.onItemClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->
                    val selected = alertHealthListBinding.list.adapter.getItem(i) as RealmUserModel
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        var libList: List<RealmMyLibrary> = emptyList()
                        databaseService.withRealm { realm ->
                            libList = getLibraryList(realm, selected._id).map { realm.copyFromRealm(it) }
                        }
                        withContext(Dispatchers.Main) {
                            showLibraryDownloadDialog(libList)
                        }
                    }
                    dialog?.dismiss()
                }

                dialog = AlertDialog.Builder(requireActivity())
                    .setTitle(getString(R.string.select_member))
                    .setView(alertHealthListBinding.root)
                    .setCancelable(false)
                    .setNegativeButton(R.string.dismiss, null)
                    .create()

                dialog.show()
            }
        }
    }

    override fun syncKeyId() {
        if (model?.getRoleAsString()?.contains("health") == true) {
            settings?.let { transactionSyncManager.syncAllHealthData(it, this) }
        } else {
            settings?.let { transactionSyncManager.syncKeyIv(it, this, profileDbHandler) }
        }
    }

    private fun showLibraryDownloadDialog(dbMyLibrary: List<RealmMyLibrary>) {
        if (!isAdded) return
        Service(requireContext()).isPlanetAvailable(object : Service.PlanetAvailableListener {
            override fun isAvailable() {
                if (!isAdded) return
                val userId = profileDbHandler.userModel?.id
                val librariesForDialog = if (userId.isNullOrBlank()) {
                    dbMyLibrary
                } else {
                    val userLibraries = dbMyLibrary.filter { it.userId?.contains(userId) == true }
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
                                addToMyLibrary(librariesForDialog, it)
                                downloadFiles(librariesForDialog, it)
                            }?.let { startDownload(it) }
                        }.setNeutralButton(R.string.download_all) { _: DialogInterface?, _: Int ->
                            lv?.selectedItemsList?.let {
                                val allItems = ArrayList<Int>()
                                librariesForDialog.indices.forEach { allItems.add(it) }
                                addToMyLibrary(librariesForDialog, allItems)
                            }
                            startDownload(downloadAllFiles(librariesForDialog))
                        }.setNegativeButton(R.string.txt_cancel, null)

                    val dialog = alertDialogBuilder.create()
                    createListView(librariesForDialog, dialog)
                    dialog.show()
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = (lv?.selectedItemsList?.size ?: 0) > 0
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

    private fun addToMyLibrary(libraryItems: List<RealmMyLibrary>, selectedItems: ArrayList<Int>) {
        val userId = profileDbHandler.userModel?.id ?: return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            databaseService.executeTransactionAsync { realm ->
                selectedItems.forEach { index ->
                    val item = libraryItems[index]
                    val dbItem = realm.where(RealmMyLibrary::class.java).equalTo("resourceId", item.resourceId).findFirst()
                    dbItem?.setUserId(userId, realm)
                    RealmRemovedLog.onAdd(realm, "resources", userId, item.resourceId)
                }
            }
            withContext(Dispatchers.Main) {
                Utilities.toast(activity, getString(R.string.added_to_my_library))
            }
        }
    }

    override fun onSyncStarted() {
        di?.show()
    }

    override fun onSyncComplete() {
        di?.dismiss()
    }

    override fun onSyncFailed(msg: String?) {
        di?.dismiss()
    }

}
