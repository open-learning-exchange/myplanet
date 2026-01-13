package org.ole.planet.myplanet.ui.dashboard

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
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
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.DashboardActionListener
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.databinding.AlertHealthListBinding
import org.ole.planet.myplanet.databinding.ItemLibraryHomeBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.TeamNotificationInfo
import org.ole.planet.myplanet.service.sync.TransactionSyncManager
import org.ole.planet.myplanet.ui.exam.UserInformationFragment
import org.ole.planet.myplanet.ui.health.UserSelectionAdapter
import org.ole.planet.myplanet.ui.teams.TeamDetailFragment
import org.ole.planet.myplanet.ui.user.BecomeMemberActivity
import org.ole.planet.myplanet.ui.user.UserProfileFragment
import org.ole.planet.myplanet.ui.voices.NewsViewModel
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.DownloadUtils
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
open class BaseDashboardFragment : BaseDashboardFragmentPlugin(), DashboardActionListener,
    OnSyncListener {
    private val viewModel: DashboardViewModel by viewModels()
    private val newsViewModel: NewsViewModel by viewModels()
    private val realm get() = requireRealmInstance()
    private var fullName: String? = null
    private var params = LinearLayout.LayoutParams(250, 100)
    private var di: DialogUtils.CustomProgressDialog? = null

    @Inject
    lateinit var transactionSyncManager: TransactionSyncManager

    fun onLoaded(v: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            model = userRepository.getUserModelSuspending()
            fullName = model?.getFullName()
            if (fullName?.trim().isNullOrBlank()) {
                fullName = model?.name
                v.findViewById<LinearLayout>(R.id.ll_prompt).visibility = View.VISIBLE
                v.findViewById<LinearLayout>(R.id.ll_prompt).setOnClickListener {
                    if (!childFragmentManager.isStateSaved) {
                        UserInformationFragment.getInstance("", "", false)
                            .show(childFragmentManager, "")
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

            if (isRealmInitialized() && mRealm.isInTransaction) {
                mRealm.commitTransaction()
            }

            v.findViewById<TextView>(R.id.txtRole).text =
                getString(R.string.user_role, model?.getRoleAsString())
        }
    }

    override fun forceDownloadNewsImages() {
        Utilities.toast(activity, getString(R.string.please_select_starting_date))
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog(requireActivity(), { _: DatePicker?, i: Int, i1: Int, i2: Int ->
            now[Calendar.YEAR] = i
            now[Calendar.MONTH] = i1
            now[Calendar.DAY_OF_MONTH] = i2
            newsViewModel.getPrivateImageUrlsCreatedAfter(now.timeInMillis) { urls ->
                if (urls.isNotEmpty()) {
                    Utilities.toast(activity, getString(R.string.downloading_images_please_check_notification))
                    DownloadUtils.openDownloadService(activity, ArrayList(urls), false)
                } else {
                    Utilities.toast(activity, getString(R.string.no_images_to_download))
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
            launch {
                viewModel.uiState
                    .map { it.library }
                    .distinctUntilChanged()
                    .collect { library ->
                        renderMyLibrary(library)
                    }
            }
            launch {
                viewModel.uiState
                    .map { it.courses }
                    .distinctUntilChanged()
                    .collect { courses ->
                        renderMyCourses(courses)
                    }
            }
            launch {
                viewModel.uiState
                    .map { it.teams }
                    .distinctUntilChanged()
                    .collect { teams ->
                        renderMyTeams(teams)
                    }
            }
            launch {
                viewModel.uiState
                    .map { it.fullName to it.offlineLogins }
                    .distinctUntilChanged()
                    .collect { (fullName, offlineLogins) ->
                        view?.findViewById<TextView>(R.id.txtFullName)?.text =
                            getString(R.string.user_name, fullName, offlineLogins)
                    }
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

        for ((count, ob) in teams.withIndex()) {
            val v = LayoutInflater.from(activity).inflate(R.layout.item_home_my_team, flexboxLayout, false)
            val name = v.findViewById<TextView>(R.id.tv_name)
            setBackgroundColor(v, count)
            if (ob.teamType == "sync") {
                name.setTypeface(null, Typeface.BOLD)
            }
            handleClick(ob._id, ob.name, TeamDetailFragment(), name)
            name.text = ob.name
            v.tag = ob._id
            flexboxLayout.addView(v, params)
        }
        setCountText(teams.size, RealmMyTeam::class.java, requireView())

        val userId = profileDbHandler.userModel?.id
        val teamIds = teams.mapNotNull { it._id }
        if (userId != null && teamIds.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                val notificationInfoMap = viewModel.getTeamNotifications(teamIds, userId)
                updateTeamNotifications(flexboxLayout, notificationInfoMap)
            }
        }
    }

    private fun updateTeamNotifications(flexboxLayout: FlexboxLayout, notificationInfoMap: Map<String, TeamNotificationInfo>) {
        for (i in 0 until flexboxLayout.childCount) {
            val teamView = flexboxLayout.getChildAt(i)
            val teamId = teamView.tag as? String
            teamId?.let { id ->
                notificationInfoMap[id]?.let { info ->
                    showNotificationIcons(teamView, info)
                }
            }
        }
    }

    private fun showNotificationIcons(v: View, info: TeamNotificationInfo) {
        val imgTask = v.findViewById<ImageView>(R.id.img_task)
        val imgChat = v.findViewById<ImageView>(R.id.img_chat)
        imgChat.visibility = if (info.hasChat) View.VISIBLE else View.GONE
        imgTask.visibility = if (info.hasTask) View.VISIBLE else View.GONE
    }

    private suspend fun myLifeListInit(flexboxLayout: FlexboxLayout) {
        val user = profileDbHandler.userModel

        val dbMylife = databaseService.withRealmAsync { realmInstance ->
            val rawMylife: List<RealmMyLife> = RealmMyLife.getMyLifeByUserId(realmInstance, settings)
            rawMylife.filter { it.isVisible }.map { realmInstance.copyFromRealm(it) }
        }

        for ((itemCnt, items) in dbMylife.withIndex()) {
            flexboxLayout.addView(getLayout(itemCnt, items, 0), params)
        }

        val surveyCount = viewModel.getSurveySubmissionCount(user?.id)
        updateMyLifeSurveyCount(flexboxLayout, surveyCount)
    }

    private fun updateMyLifeSurveyCount(flexboxLayout: FlexboxLayout, surveyCount: Int) {
        // Update views with survey count if needed
    }

    private suspend fun setUpMyLife(userId: String?) {
        databaseService.executeTransactionAsync { realm ->
            val realmObjects = RealmMyLife.getMyLifeByUserId(realm, settings)
            if (realmObjects.isEmpty()) {
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

    override fun onDestroy() {
        if (isRealmInitialized()) {
            mRealm.removeAllChangeListeners()
            if (mRealm.isInTransaction) {
                mRealm.cancelTransaction()
            }
            mRealm.close()
        }
        super.onDestroy()
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

        val userId = settings?.getString("userId", "--")
        viewModel.loadUserContent(userId)
        observeUiState()

        view.findViewById<FlexboxLayout>(R.id.flexboxLayoutCourse).flexDirection = FlexDirection.ROW
        view.findViewById<FlexboxLayout>(R.id.flexboxLayoutTeams).flexDirection = FlexDirection.ROW
        val myLifeFlex = view.findViewById<FlexboxLayout>(R.id.flexboxLayoutMyLife)
        myLifeFlex.flexDirection = FlexDirection.ROW

        viewLifecycleOwner.lifecycleScope.launch {
            setUpMyLife(userId)
            myLifeListInit(myLifeFlex)
        }


        if (isRealmInitialized() && mRealm.isInTransaction) {
            mRealm.commitTransaction()
        }
    }

    override fun showResourceDownloadDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val userId = settings?.getString("userId", "--")
            val libraryList = resourcesRepository.getLibraryListForUser(userId)
            showDownloadDialog(libraryList)
        }
    }

    override fun showUserResourceDialog() {
        viewModel.loadUsers()

        val alertHealthListBinding = AlertHealthListBinding.inflate(LayoutInflater.from(activity))
        alertHealthListBinding.etSearch.visibility = View.GONE
        alertHealthListBinding.spnSort.visibility = View.GONE
        alertHealthListBinding.loading.visibility = View.VISIBLE
        alertHealthListBinding.list.visibility = View.GONE

        alertHealthListBinding.btnAddMember.setOnClickListener {
            startActivity(Intent(requireContext(), BecomeMemberActivity::class.java))
        }

        val dialog = AlertDialog.Builder(requireActivity())
            .setTitle(getString(R.string.select_member))
            .setView(alertHealthListBinding.root)
            .setCancelable(false)
            .setNegativeButton(R.string.dismiss, null)
            .create()

        val job = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect {
                if (dialog.isShowing) {
                    if (it.users.isNotEmpty()) {
                        val adapter = UserSelectionAdapter(requireActivity(), android.R.layout.simple_list_item_1, it.users)
                        alertHealthListBinding.list.adapter = adapter
                        alertHealthListBinding.list.onItemClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->
                            val selected = alertHealthListBinding.list.adapter.getItem(i) as RealmUserModel
                            selected._id?.let { userId ->
                                viewLifecycleOwner.lifecycleScope.launch {
                                    val libraryList = viewModel.getLibraryForSelectedUser(userId)
                                    showDownloadDialog(libraryList)
                                }
                            }
                            dialog.dismiss()
                        }
                        alertHealthListBinding.list.visibility = View.VISIBLE
                    } else {
                        alertHealthListBinding.list.visibility = View.GONE
                    }
                    alertHealthListBinding.loading.visibility = View.GONE
                }
            }
        }

        dialog.setOnDismissListener { job.cancel() }
        dialog.show()
    }

    override fun syncKeyId() {
        if (model?.getRoleAsString()?.contains("health") == true) {
            settings?.let { transactionSyncManager.syncAllHealthData(realm, it, this) }
        } else {
            settings?.let { transactionSyncManager.syncKeyIv(realm, it, this, profileDbHandler) }
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
