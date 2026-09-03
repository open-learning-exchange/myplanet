package org.ole.planet.myplanet.base

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayout
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertHealthListBinding
import org.ole.planet.myplanet.databinding.ItemLibraryHomeBinding
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.TeamNotificationInfo
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.repository.SyncUiState
import org.ole.planet.myplanet.ui.courses.CoursesFragment
import org.ole.planet.myplanet.ui.dashboard.DashboardItem
import org.ole.planet.myplanet.ui.dashboard.DashboardPluginFragment
import org.ole.planet.myplanet.ui.dashboard.DashboardViewModel
import org.ole.planet.myplanet.ui.dashboard.ItemType
import org.ole.planet.myplanet.ui.exam.UserInformationFragment
import org.ole.planet.myplanet.ui.health.HealthUsersAdapter
import org.ole.planet.myplanet.ui.life.LifeFragment
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.teams.TeamDetailFragment
import org.ole.planet.myplanet.ui.teams.TeamFragment
import org.ole.planet.myplanet.ui.user.BecomeMemberActivity
import org.ole.planet.myplanet.ui.user.UserProfileFragment
import org.ole.planet.myplanet.ui.voices.NewsViewModel
import org.ole.planet.myplanet.utils.DialogUtils
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.ImageUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
open class BaseDashboardFragment : DashboardPluginFragment() {
    private val viewModel: DashboardViewModel by viewModels()
    private val newsViewModel: NewsViewModel by viewModels()
    protected var userLibrary: List<MyLibrary> = emptyList()
    protected var userCourses: List<MyCourse> = emptyList()
    protected var userTeams: List<MyTeam> = emptyList()
    private var fullName: String? = null
    private fun createChipLayoutParams(): FlexboxLayout.LayoutParams =
        FlexboxLayout.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.dashboard_chip_width),
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            flexShrink = 0f
            marginEnd = resources.getDimensionPixelSize(R.dimen.dashboard_chip_gap)
        }
    private var di: DialogUtils.CustomProgressDialog? = null

    @Inject
    lateinit var lifeRepository: LifeRepository

    fun onLoaded(v: View) {
        val llPrompt = v.findViewById<LinearLayout>(R.id.ll_prompt)
        val icClose = v.findViewById<ImageView>(R.id.ic_close)
        val imageView = v.findViewById<ImageView>(R.id.imageView)

        viewLifecycleOwner.lifecycleScope.launch {
            model = userRepository.getUserProfile()
            fullName = model?.getFullName()
            if (fullName?.trim().isNullOrBlank()) {
                fullName = model?.name
                llPrompt.visibility = View.VISIBLE
                llPrompt.setOnClickListener {
                    if (!childFragmentManager.isStateSaved) {
                        UserInformationFragment.getInstance("", "", false)
                            .show(childFragmentManager, "")
                    }
                }
            } else {
                llPrompt.visibility = View.GONE
            }
            icClose.setOnClickListener {
                llPrompt.visibility = View.GONE
            }
            ImageUtils.loadProfileImage(model?.userImage, imageView, 200)

            v.findViewById<TextView>(R.id.txtRole).text =
                getString(R.string.user_role, model?.getRoleAsString())
        }
    }

    fun forceDownloadNewsImages() {
        Utilities.toast(activity, getString(R.string.please_select_starting_date))
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog(requireActivity(), { _: DatePicker?, i: Int, i1: Int, i2: Int ->
            now[Calendar.YEAR] = i
            now[Calendar.MONTH] = i1
            now[Calendar.DAY_OF_MONTH] = i2
            newsViewModel.getPrivateImageUrlsCreatedAfter(now.timeInMillis)
        }, now[Calendar.YEAR], now[Calendar.MONTH], now[Calendar.DAY_OF_MONTH])
        dpd.setTitle(getString(R.string.read_offline_news_from))
        dpd.show()
    }

    private fun observeUiState() {
        collectWhenStarted(viewModel.uiState.map { it.library }.distinctUntilChanged()) { library ->
            renderMyLibrary(library)
        }
        collectWhenStarted(viewModel.uiState.map { it.courses }.distinctUntilChanged()) { courses ->
            renderMyCourses(courses)
        }
        collectWhenStarted(viewModel.uiState.map { it.teams }.distinctUntilChanged()) { teams ->
            renderMyTeams(teams)
        }
        collectWhenStarted(viewModel.uiState.map { it.fullName to it.offlineLogins }.distinctUntilChanged()) { (fullName, offlineLogins) ->
            view?.findViewById<TextView>(R.id.txtFullName)?.text =
                getString(R.string.user_name, fullName, offlineLogins)
        }
        collectWhenStarted(newsViewModel.privateImageUrls) { urls ->
            if (urls.isNotEmpty()) {
                Utilities.toast(activity, getString(R.string.downloading_images_please_check_notification))
                DownloadUtils.openDownloadService(activity, ArrayList(urls), false)
            } else {
                Utilities.toast(activity, getString(R.string.no_images_to_download))
            }
        }
    }

    private fun renderPlaceholder(
        flexboxLayout: FlexboxLayout,
        message: String,
        onClick: (() -> Unit)? = null
    ) {
        val itemLibraryHomeBinding =
            ItemLibraryHomeBinding.inflate(LayoutInflater.from(activity))
        val v = itemLibraryHomeBinding.root
        itemLibraryHomeBinding.title.text = message
        itemLibraryHomeBinding.title.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.hint_color)
        )
        itemLibraryHomeBinding.chipIcon.visibility = View.GONE
        itemLibraryHomeBinding.detail.visibility = View.GONE
        if (onClick != null) {
            v.setOnClickListener { onClick() }
        }
        flexboxLayout.addView(v, createChipLayoutParams())
    }

    private fun renderMyLibrary(dbMylibrary: List<MyLibrary>) {
        userLibrary = dbMylibrary
        val flexboxLayout = view?.findViewById<FlexboxLayout>(R.id.flexboxLayout)
        flexboxLayout?.removeAllViews()
        flexboxLayout?.flexDirection = FlexDirection.ROW
        val countView = view?.findViewById<TextView>(R.id.count_library)
        if (dbMylibrary.isEmpty()) {
            countView?.visibility = View.GONE
            flexboxLayout?.let {
                renderPlaceholder(it, getString(R.string.no_resources_added_yet)) {
                    if (model?.id?.startsWith("guest") == true) {
                        DialogUtils.guestDialog(requireContext())
                    } else {
                        homeItemClickListener?.openCallFragment(ResourcesFragment())
                    }
                }
            }
            return
        } else {
            countView?.visibility = View.VISIBLE
            countView?.text = getString(R.string.number_placeholder, dbMylibrary.size)
        }
        for (items in dbMylibrary) {
            val itemLibraryHomeBinding =
                ItemLibraryHomeBinding.inflate(LayoutInflater.from(activity))
            val v = itemLibraryHomeBinding.root

            itemLibraryHomeBinding.title.text = items.title
            itemLibraryHomeBinding.detail.setOnClickListener {
                if (homeItemClickListener != null) {
                    homeItemClickListener?.openLibraryDetailFragment(items)
                }
            }

            myLibraryItemClickAction(itemLibraryHomeBinding.title, items)
            flexboxLayout?.addView(v, createChipLayoutParams())
        }
    }

    private fun renderMyCourses(courses: List<MyCourse>) {
        val flexboxLayout: FlexboxLayout = view?.findViewById(R.id.flexboxLayoutCourse) ?: return
        flexboxLayout.removeAllViews()
        val filteredCourses = courses.filter { !it.courseTitle.isNullOrBlank() }
        userCourses = filteredCourses
        setCountText(filteredCourses.size, MyCourse::class.java, requireView())
        if (filteredCourses.isEmpty()) {
            renderPlaceholder(flexboxLayout, getString(R.string.no_courses_joined_yet)) {
                if (model?.id?.startsWith("guest") == true) {
                    DialogUtils.guestDialog(requireContext())
                } else {
                    homeItemClickListener?.openMyFragment(CoursesFragment())
                }
            }
            return
        }
        for (items in filteredCourses) {
            val dashboardItem = DashboardItem(items.courseId, items.courseTitle, null, ItemType.COURSE)
            flexboxLayout.addView(createCourseChip(dashboardItem), createChipLayoutParams())
        }
    }

    private suspend fun renderMyTeams(teams: List<MyTeam>) {
        userTeams = teams
        val flexboxLayout: FlexboxLayout = view?.findViewById(R.id.flexboxLayoutTeams) ?: return
        flexboxLayout.removeAllViews()
        setCountText(teams.size, MyTeam::class.java, requireView())
        if (teams.isEmpty()) {
            renderPlaceholder(flexboxLayout, getString(R.string.no_teams_joined_yet)) {
                homeItemClickListener?.openCallFragment(TeamFragment())
            }
            return
        }

        for (ob in teams) {
            val v = LayoutInflater.from(activity).inflate(R.layout.item_home_my_team, flexboxLayout, false)
            val name = v.findViewById<TextView>(R.id.tv_name)
            if (ob.teamType == "sync") {
                name.setTypeface(null, Typeface.BOLD)
            }
            handleClick(ob._id, ob.name, TeamDetailFragment(), name)
            name.text = ob.name
            v.tag = ob._id
            flexboxLayout.addView(v, createChipLayoutParams())
        }

        val userId = userRepository.getUserModel()?.id
        val teamIds = teams.mapNotNull { it._id }
        if (userId != null && teamIds.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                val notificationInfoMap = viewModel.getTeamNotifications(teamIds, userId)
                updateTeamNotifications(flexboxLayout, notificationInfoMap)
            }
        }
    }

    private fun updateTeamNotifications(
        flexboxLayout: FlexboxLayout,
        notificationInfoMap: Map<String, TeamNotificationInfo>
    ) {
        for (i in 0 until flexboxLayout.childCount) {
            val child = flexboxLayout.getChildAt(i)
            val teamId = child.tag as? String ?: continue
            val info = notificationInfoMap[teamId] ?: continue
            showNotificationIcons(child, info)
        }
    }

    private fun showNotificationIcons(v: View, info: TeamNotificationInfo) {
        val imgTask = v.findViewById<ImageView>(R.id.img_task)
        val imgChat = v.findViewById<ImageView>(R.id.img_chat)
        imgChat.visibility = if (info.hasChat) View.VISIBLE else View.GONE
        imgTask.visibility = if (info.hasTask) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        refreshMyLifeList()
    }

    protected fun refreshMyLifeList(view: View? = this.view) {
        val v = view ?: return
        val myLifeFlex = v.findViewById<FlexboxLayout>(R.id.flexboxLayoutMyLife) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            myLifeListInit(myLifeFlex)
        }
    }

    private suspend fun myLifeListInit(flexboxLayout: FlexboxLayout) {
        flexboxLayout.removeAllViews()
        val userId = prefData.getUserId().ifEmpty { "--" }
        val visibleItems = lifeRepository.getMyLifeForDashboard(userId, getMyLifeListBase(userId))
        if (visibleItems.isEmpty()) {
            renderPlaceholder(flexboxLayout, getString(R.string.no_data_available)) {
                homeItemClickListener?.openCallFragment(org.ole.planet.myplanet.ui.life.LifeFragment())
            }
            return
        }
        for (items in visibleItems) {
            val dashboardItem = DashboardItem(items._id, items.title, items.imageId, ItemType.LIFE)
            flexboxLayout.addView(getLayout(dashboardItem, 0), createChipLayoutParams())
        }
        updateMyLifeSurveyCount()
    }

    private fun updateMyLifeSurveyCount() {
        // Update views with survey count if needed
    }

    private fun myLibraryItemClickAction(textView: TextView, items: MyLibrary?) {
        textView.setOnClickListener {
            items?.let {
                openResource(it)
            }
        }
    }

    private fun setCountText(countText: Int, c: Class<*>, v: View) {
        when (c) {
            MyCourse::class.java -> {
                updateCountText(countText, v.findViewById(R.id.count_course))
            }
            MyTeam::class.java -> {
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

        val userId = prefData.getUserId().ifEmpty { "--" }
        viewModel.loadUserContent(userId)
        observeUiState()

        view.findViewById<FlexboxLayout>(R.id.flexboxLayoutCourse)?.flexDirection = FlexDirection.ROW
        view.findViewById<FlexboxLayout>(R.id.flexboxLayoutTeams)?.flexDirection = FlexDirection.ROW
        view.findViewById<FlexboxLayout>(R.id.flexboxLayoutMyLife)?.flexDirection = FlexDirection.ROW

        collectWhenStarted(viewModel.syncKeyIdEvent) { state ->
            when (state) {
                is SyncUiState.Loading -> onSyncStarted()
                is SyncUiState.Success -> onSyncComplete()
                is SyncUiState.Error -> onSyncFailed(state.message)
                else -> {}
            }
        }
    }

    fun showResourceDownloadDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val userId = prefData.getUserId().ifEmpty { "--" }
            val libraryList = viewModel.getLibraryListForUser(userId)
            showDownloadDialog(libraryList)
        }
    }

    fun showUserResourceDialog() {
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

        val adapter = HealthUsersAdapter { selected ->
            selected._id?.let { userId ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val libraryList = viewModel.getLibraryListForUser(userId)
                    showDownloadDialog(libraryList)
                }
            }
            dialog.dismiss()
        }
        alertHealthListBinding.list.layoutManager = LinearLayoutManager(requireActivity())
        alertHealthListBinding.list.adapter = adapter

        val job = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect {
                if (dialog.isShowing) {
                    if (it.users.isNotEmpty()) {
                        adapter.submitList(it.users)
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

    fun syncKeyId() {
        viewModel.syncKeyId(model?.getRoleAsString())
    }

    fun onSyncStarted() {
        di?.show()
    }

    fun onSyncComplete() {
        di?.dismiss()
    }

    fun onSyncFailed(msg: String?) {
        di?.dismiss()
    }
}
